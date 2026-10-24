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
from bollettino_allergie.catalogo import PER_ID, ritardo_fenologico, verso_ritardo
from bollettino_allergie.cli import elenco, formatta, main
from bollettino_allergie.web import Gestore

DATI = Path(__file__).resolve().parent.parent / "esempi" / "dati"
ROMA = Luogo("Roma", 41.89, 12.51, "Lazio", "Italia")


def carica(nome):
    dati = json.loads((DATI / f"{nome}.json").read_text())
    return Luogo(**dati.pop("luogo")), dati


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


class TestInterfacce(unittest.TestCase):
    def setUp(self):
        luogo, dati = carica("milano_agosto")
        for nome, valore in (("cerca_luogo", luogo), ("scarica_previsioni", dati)):
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
