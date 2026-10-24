import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path
from unittest import mock

from bollettino_allergie import Luogo, cerca_pollini, crea_bollettino
from bollettino_allergie.cli import formatta, main
from bollettino_allergie.pollini import PER_CHIAVE
from bollettino_allergie.web import Gestore

DATI = Path(__file__).resolve().parent.parent / "esempi" / "dati"
ROMA = Luogo("Roma", 41.89, 12.51, "Lazio", "Italia")


def carica(nome):
    dati = json.loads((DATI / f"{nome}.json").read_text())
    return Luogo(**dati.pop("luogo")), dati


class TestSoglie(unittest.TestCase):
    def test_livelli_graminacee(self):
        p = PER_CHIAVE["grass_pollen"]
        self.assertEqual(p.livello(0.2), "assente")
        self.assertEqual(p.livello(0.6), "basso")
        self.assertEqual(p.livello(10), "medio")
        self.assertEqual(p.livello(30), "alto")
        self.assertIsNone(p.livello(None))

    def test_cerca_pollini_alias(self):
        nomi = [p.nome for p in cerca_pollini(["Betulla", "grass", "betulla"])]
        self.assertEqual(nomi, ["Betulla", "Graminacee"])
        with self.assertRaises(ValueError):
            cerca_pollini(["cipresso"])


class TestBollettino(unittest.TestCase):
    def test_media_e_picco_giornalieri(self):
        dati = {"hourly": {
            "time": ["2026-05-01T00:00", "2026-05-01T12:00", "2026-05-02T00:00"],
            "olive_pollen": [10, 30, None],
        }}
        b = crea_bollettino(ROMA, dati, cerca_pollini(["olivo"]))
        self.assertEqual([g.data for g in b.giorni], ["2026-05-01", "2026-05-02"])
        r = b.giorni[0].rilevazioni[0]
        self.assertEqual((r.media, r.picco, r.livello), (20.0, 30.0, "medio"))
        self.assertIsNone(b.giorni[1].rilevazioni[0].media)
        self.assertEqual(b.giorni[1].consiglio, "Dati non disponibili.")

    def test_scenario_roma_maggio(self):
        b = crea_bollettino(*carica("roma_maggio"))
        olivo = {g.data: next(r for r in g.rilevazioni if r.polline == "Olivo")
                 for g in b.giorni}
        self.assertEqual(olivo["2026-05-19"].media, 41.0)
        self.assertEqual(olivo["2026-05-19"].livello, "alto")
        self.assertTrue(all(g.livello_massimo == "alto" for g in b.giorni))

    def test_to_dict_e_formatta(self):
        b = crea_bollettino(*carica("milano_aprile"), cerca_pollini(["betulla"]))
        d = b.to_dict()
        self.assertEqual(d["luogo"]["descrizione"], "Milano, Lombardia, Italia")
        self.assertEqual(d["giorni"][2]["livello_massimo"], "medio")
        self.assertIn("Betulla       88.0", formatta(b))


class TestInterfacce(unittest.TestCase):
    def setUp(self):
        luogo, dati = carica("milano_agosto")
        for nome, valore in (("cerca_luogo", luogo), ("scarica_pollini", dati)):
            for modulo in ("cli", "web"):
                p = mock.patch(f"bollettino_allergie.{modulo}.{nome}", return_value=valore)
                p.start()
                self.addCleanup(p.stop)

    def test_cli_json(self):
        with mock.patch("builtins.print") as stampa:
            self.assertEqual(main(["Milano", "--json", "-p", "ambrosia"]), 0)
        d = json.loads(stampa.call_args.args[0])
        self.assertEqual([g["pollini"][0]["livello"] for g in d["giorni"]],
                         ["medio", "alto", "alto"])

    def test_web(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), Gestore)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        base = f"http://127.0.0.1:{server.server_port}"
        with urllib.request.urlopen(base + "/") as r:
            self.assertIn(b"Bollettino Allergie", r.read())
        with urllib.request.urlopen(base + "/api/bollettino?luogo=Milano&pollini=ambrosia") as r:
            d = json.load(r)
        self.assertEqual(d["giorni"][2]["pollini"][0]["media"], 52.0)
        with self.assertRaises(urllib.error.HTTPError) as e:
            urllib.request.urlopen(base + "/api/bollettino")
        self.assertEqual(e.exception.code, 400)


if __name__ == "__main__":
    unittest.main()
