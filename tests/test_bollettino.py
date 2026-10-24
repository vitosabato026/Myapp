import json
import threading
import unittest
import urllib.error
import urllib.request
from datetime import date
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest import mock

from bollettino_allergie import ALLERGENI, Luogo, cerca_allergeni, crea_bollettino
from bollettino_allergie import pollnet
from bollettino_allergie.catalogo import PER_ID, ritardo_fenologico, verso_ritardo
from bollettino_allergie.cli import elenco, formatta, in_evidenza, main
from bollettino_allergie.web import Gestore

DATI = Path(__file__).resolve().parent.parent / "esempi" / "dati"
ROMA = Luogo("Roma", 41.89, 12.51, "Lazio", "Italia")


def carica(nome):
    dati = json.loads((DATI / f"{nome}.json").read_text())
    return Luogo(**dati.pop("luogo")), dati


def carica_pollnet():
    dati = json.loads((DATI / "pollnet_24ottobre.json").read_text())
    stazioni = [pollnet.Stazione(**s) for s in dati["stazioni"]]
    return date.fromisoformat(dati["oggi"]), stazioni, {int(k): v for k, v in dati["record"].items()}


def rilevazione(giorno, id_):
    return next(r for r in giorno.rilevazioni if r.id == id_)


class TestCatalogo(unittest.TestCase):
    def test_contenuto(self):
        ids = {a.id for a in ALLERGENI}
        for atteso in ("graminacee", "cipresso", "parietaria", "nocciolo", "alternaria", "pm10", "ozono"):
            self.assertIn(atteso, ids)
        self.assertEqual(len(ids), len(ALLERGENI), "id duplicati")
        for a in ALLERGENI:
            self.assertEqual(len(a.soglie), 3)
            self.assertEqual(a.variabile is not None, a.tipo == "previsione", a.id)
            if a.tipo == "stagionale":
                self.assertEqual(len(a.calendario), 12, a.id)

    def test_livelli_graminacee(self):
        p = PER_ID["graminacee"]
        self.assertEqual([p.livello(v) for v in (0.2, 0.6, 10, 30)],
                         ["assente", "basso", "medio", "alto"])
        self.assertIsNone(p.livello(None))

    def test_cerca_allergeni(self):
        trovati = [a.id for a in cerca_allergeni(["Betulla", "grass", "ortica", "muffa", "betulla"])]
        self.assertEqual(trovati, ["betulla", "graminacee", "parietaria", "alternaria"])
        with self.assertRaises(ValueError):
            cerca_allergeni(["kiwi"])


class TestStagionale(unittest.TestCase):
    def test_calendario(self):
        cipresso = PER_ID["cipresso"]
        self.assertEqual(cipresso.indice_stagionale(date(2026, 2, 15)), 3.0)
        self.assertEqual(cipresso.indice_stagionale(date(2026, 7, 15)), 0.0)

    def test_ritardo_hopkins(self):
        self.assertAlmostEqual(ritardo_fenologico(43, 0), 0)
        self.assertAlmostEqual(ritardo_fenologico(45, 122), 12)
        self.assertEqual(ritardo_fenologico(70, 3000), 60)  # limitato

    def test_autunno_al_contrario(self):
        self.assertEqual(verso_ritardo(100), 1)
        self.assertEqual(verso_ritardo(280), -1)
        # a nord la stagione autunnale della parietaria finisce prima che a sud
        p = PER_ID["parietaria"]
        ottobre = date(2026, 10, 24)
        self.assertGreater(p.indice_stagionale(ottobre, ritardo_fenologico(38, 0)),
                           p.indice_stagionale(ottobre, ritardo_fenologico(46, 0)))
        # in primavera il contrario: a nord la fioritura arriva dopo
        castagno = PER_ID["castagno"]
        giugno = date(2026, 6, 1)
        self.assertGreater(castagno.indice_stagionale(giugno, ritardo_fenologico(38, 0)),
                           castagno.indice_stagionale(giugno, ritardo_fenologico(46, 0)))


class TestBollettino(unittest.TestCase):
    def test_media_e_picco_giornalieri(self):
        dati = {"hourly": {
            "time": ["2026-05-01T00:00", "2026-05-01T12:00", "2026-05-02T00:00"],
            "olive_pollen": [10, 30, None],
            "ozone": [60, 140, None],
        }}
        b = crea_bollettino(ROMA, dati, cerca_allergeni(["olivo", "ozono"]))
        self.assertEqual([g.data for g in b.giorni], ["2026-05-01", "2026-05-02"])
        olivo, ozono = b.giorni[0].rilevazioni
        self.assertEqual((olivo.media, olivo.picco, olivo.livello), (20.0, 30.0, "medio"))
        self.assertEqual((ozono.media, ozono.valore, ozono.livello), (100.0, 140.0, "alto"))  # usa il picco
        self.assertIsNone(b.giorni[1].rilevazioni[0].media)
        self.assertEqual(b.giorni[1].consiglio, "Dati non disponibili.")

    def test_fuori_europa_niente_stime(self):
        dati = {"hourly": {"time": ["2026-05-01T00:00"], "grass_pollen": [None], "pm10": [30]}}
        g = crea_bollettino(ROMA, dati, cerca_allergeni(["cipresso", "pm10"])).giorni[0]
        self.assertIsNone(rilevazione(g, "cipresso").livello)
        self.assertEqual(rilevazione(g, "pm10").livello, "basso")

    def test_scenario_roma_maggio(self):
        b = crea_bollettino(*carica("roma_maggio"))
        g = b.giorni[1]
        self.assertEqual(rilevazione(g, "olivo").media, 41.0)
        self.assertEqual(rilevazione(g, "olivo").livello, "alto")
        self.assertEqual(rilevazione(g, "quercia").livello, "alto")  # stima: piena fioritura a maggio
        self.assertEqual(rilevazione(g, "parietaria").livello, "alto")
        self.assertEqual(rilevazione(g, "cipresso").livello, "assente")
        self.assertEqual(rilevazione(b.giorni[2], "polvere").livello, "medio")
        self.assertTrue(all(x.livello_massimo == "alto" for x in b.giorni))
        self.assertIn("Aria inquinata", b.giorni[2].consiglio)
        self.assertEqual(len(g.rilevazioni), len(ALLERGENI))

    def test_to_dict_e_formatta(self):
        b = crea_bollettino(*carica("milano_aprile"), cerca_allergeni(["betulla", "no2"]))
        d = b.to_dict()
        self.assertEqual(d["luogo"]["descrizione"], "Milano, Lombardia, Italia")
        self.assertEqual(d["giorni"][2]["livello_massimo"], "medio")
        self.assertEqual(d["giorni"][1]["allergeni"][1]["livello"], "medio")
        testo = formatta(b)
        self.assertIn("Betulla", testo)
        self.assertIn("88.0", testo)
        self.assertIn("Qualità dell'aria", testo)

    def test_formatta_riassume_assenti(self):
        b = crea_bollettino(*carica("milano_agosto"))
        testo = formatta(b)
        self.assertIn("assenti:", testo)
        self.assertNotIn("Cipresso*", testo)
        self.assertIn("Cipresso*", formatta(b, completo=True))
        self.assertIn("parietaria", elenco())


class TestPollnet(unittest.TestCase):
    """Usa un'istantanea reale del servizio POLLnet (24 ottobre 2026)."""

    def setUp(self):
        self.oggi, self.stazioni, self.record = carica_pollnet()
        self.per_nome = {s.nome: s for s in self.stazioni}

    def misure(self, nome):
        s = self.per_nome[nome]
        return pollnet.misure_da_record(self.record[s.id], s, 2.0, self.oggi)

    def test_parietaria_napoli(self):
        m = self.misure("Napoli - Via don Bosco")[1362]
        self.assertEqual((m.dal, m.al, m.media, m.picco), ("2026-10-11", "2026-10-17", 7.6, 10))
        self.assertEqual([v for _, v in m.serie], [8, 5, 5, 7, 10, 10, 8])

    def test_giorni_tutti_a_zero_non_sono_misure(self):
        # Roma Tor Vergata ha pubblicato 4-10 ottobre con tutte le concentrazioni a zero
        self.assertEqual(self.misure("Roma Tor Vergata"), {})

    def test_date_future_scartate(self):
        s = self.per_nome["Napoli - Via don Bosco"]
        record = self.record[s.id] + [{"PART_ID": 1362, "REMA_DATE": "2026-11-07Z", "REMA_CONCENTRATION": 500}]
        self.assertEqual(pollnet.misure_da_record(record, s, 2.0, self.oggi)[1362].al, "2026-10-17")

    def test_dati_vecchi_scartati(self):
        s = self.per_nome["Napoli - Via don Bosco"]
        tardi = date(2026, 11, 15)
        self.assertEqual(pollnet.misure_da_record(self.record[s.id], s, 2.0, tardi), {})

    def test_combina_carpino(self):
        m = self.misure("Bolzano")
        carpino = pollnet.combina(m, PER_ID["carpino"].pollnet_particelle)
        self.assertEqual(carpino.media, round(m[1381].media + m[1382].media, 1))
        self.assertIsNone(pollnet.combina(m, (1381, 99999)))
        self.assertIsNone(pollnet.combina(m, ()))  # allergene non misurato da POLLnet

    def test_stazioni_vicine(self):
        vicine = pollnet.stazioni_vicine(self.stazioni, 41.89, 12.51)  # Roma
        self.assertEqual(vicine[0][0].nome, "Roma Tor Vergata")
        self.assertLess(vicine[0][1], 15)
        self.assertTrue(all(d <= pollnet.DISTANZA_MAX_KM for _, d in vicine))
        self.assertEqual(pollnet.stazioni_vicine(self.stazioni, 40.7, -74.0), [])  # New York

    def test_scarica_misure_ripiega_sulla_stazione_successiva(self):
        roma, napoli = self.per_nome["Roma Tor Vergata"], self.per_nome["Napoli - Via don Bosco"]

        def wfs(tipo, filtro=None, campi=None):
            for s in (roma, napoli):
                if filtro and filtro.startswith(f"STAT_ID={s.id} "):
                    return self.record[s.id]
            return []

        # la stazione più vicina (Roma) ha solo giorni non analizzati: si passa alla seguente
        with mock.patch.object(pollnet, "scarica_stazioni", return_value=[roma, napoli]), \
                mock.patch.object(pollnet, "stazioni_vicine", return_value=[(roma, 9.0), (napoli, 40.0)]), \
                mock.patch.object(pollnet, "_wfs", side_effect=wfs):
            misure = pollnet.scarica_misure(41.0, 13.0, self.oggi)
        self.assertEqual(misure["parietaria"].stazione, "Napoli - Via don Bosco")
        self.assertNotIn("cladosporium", misure)  # non misurato da POLLnet

    def test_scarica_misure_servizio_non_disponibile(self):
        with mock.patch.object(pollnet, "scarica_stazioni", side_effect=pollnet.ErroreAPI("giù")):
            with self.assertRaises(pollnet.ErroreAPI):
                pollnet.scarica_misure(40.85, 14.27, self.oggi)
        with mock.patch.object(pollnet, "scarica_stazioni", return_value=[]):  # nessuna stazione
            self.assertEqual(pollnet.scarica_misure(40.7, -74.0, self.oggi), {})

    def test_cli_servizio_non_disponibile(self):
        luogo, dati = carica("roma_maggio")
        with mock.patch("bollettino_allergie.cli.cerca_luogo", return_value=luogo), \
                mock.patch("bollettino_allergie.cli.scarica_previsioni", return_value=dati), \
                mock.patch("bollettino_allergie.cli.scarica_misure", side_effect=pollnet.ErroreAPI("giù")), \
                mock.patch("builtins.print") as stampa:
            self.assertEqual(main(["Roma", "-a", "parietaria"]), 0)
        self.assertIn("stime stagionali", stampa.call_args_list[0].args[0])

    def test_filtro_include_i_totali(self):
        f = pollnet.filtro_stazione(131, [1362], self.oggi)
        self.assertIn("PART_ID IN (1,2,1362)", f)
        self.assertIn("REMA_DATE<='2026-10-24'", f)

    def test_bollettino_con_misure(self):
        s = self.per_nome["Napoli - Via don Bosco"]
        misure = pollnet.misure_da_record(self.record[s.id], s, 1.9, self.oggi)
        per_allergene = {a.id: m for a in ALLERGENI if a.pollnet_particelle
                         and (m := pollnet.combina(misure, a.pollnet_particelle))}
        napoli = Luogo("Napoli", 40.85, 14.27)
        dati = {"hourly": {"time": ["2026-10-24T00:00"], "grass_pollen": [0.1]}}
        b = crea_bollettino(napoli, dati, cerca_allergeni(["parietaria", "pino", "cladosporium"]), per_allergene)
        g = b.giorni[0]
        par = rilevazione(g, "parietaria")
        self.assertEqual((par.tipo, par.unita, par.media, par.livello), ("misura", "granuli/m³", 7.6, "basso"))
        self.assertEqual(par.misura["stazione"], "Napoli - Via don Bosco")
        self.assertEqual(rilevazione(g, "pino").livello, "medio")
        self.assertEqual(rilevazione(g, "cladosporium").tipo, "stagionale")
        self.assertEqual(b.to_dict()["giorni"][0]["allergeni"][0]["soglie"], (2, 20, 70))
        evidenza = "\n".join(in_evidenza(g.rilevazioni))
        self.assertIn("livello BASSO", evidenza)
        self.assertIn("8 5 5 7 10 10 8", evidenza)

    def test_evidenza_senza_stazione(self):
        g = crea_bollettino(*carica("roma_maggio"), cerca_allergeni(["parietaria"])).giorni[0]
        self.assertIn("stima dal calendario", "\n".join(in_evidenza(g.rilevazioni)))
        self.assertEqual(in_evidenza([]), [])


class TestInterfacce(unittest.TestCase):
    def setUp(self):
        luogo, dati = carica("milano_agosto")
        for nome, valore in (("cerca_luogo", luogo), ("scarica_previsioni", dati), ("scarica_misure", {})):
            for modulo in ("cli", "web"):
                p = mock.patch(f"bollettino_allergie.{modulo}.{nome}", return_value=valore)
                p.start()
                self.addCleanup(p.stop)

    def test_cli_json(self):
        with mock.patch("builtins.print") as stampa:
            self.assertEqual(main(["Milano", "--json", "-a", "ambrosia,ozono"]), 0)
        d = json.loads(stampa.call_args.args[0])
        self.assertEqual([g["allergeni"][0]["livello"] for g in d["giorni"]], ["medio", "alto", "alto"])
        self.assertEqual([g["allergeni"][1]["livello"] for g in d["giorni"]], ["alto"] * 3)

    def test_cli_vecchia_opzione_pollini(self):
        with mock.patch("builtins.print"):
            self.assertEqual(main(["Milano", "-p", "ambrosia"]), 0)

    def test_web(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), Gestore)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        base = f"http://127.0.0.1:{server.server_port}"
        with urllib.request.urlopen(base + "/") as r:
            self.assertIn(b"Bollettino Allergie", r.read())
        with urllib.request.urlopen(base + "/api/bollettino?luogo=Milano&allergie=ambrosia,alternaria") as r:
            d = json.load(r)
        self.assertEqual(d["giorni"][2]["allergeni"][0]["media"], 52.0)
        self.assertEqual(d["giorni"][2]["allergeni"][1]["tipo"], "stagionale")
        self.assertIn("aria", d["categorie"])
        with self.assertRaises(urllib.error.HTTPError) as e:
            urllib.request.urlopen(base + "/api/bollettino")
        self.assertEqual(e.exception.code, 400)


if __name__ == "__main__":
    unittest.main()
