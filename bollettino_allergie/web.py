"""Piccola interfaccia web basata solo sulla libreria standard.

GET /                       pagina HTML
GET /api/bollettino?luogo=Roma&giorni=3&allergie=graminacee,cipresso   JSON
"""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from importlib import resources
from urllib.parse import parse_qs, urlparse

from .api import ErroreAPI, cerca_luogo, scarica_previsioni
from .bollettino import crea_bollettino
from .catalogo import cerca_allergeni
from .pollnet import scarica_misure


def bollettino_json(query: dict[str, list[str]]) -> tuple[int, dict]:
    luogo = (query.get("luogo") or [""])[0].strip()
    if not luogo:
        return 400, {"errore": "Parametro 'luogo' mancante"}
    try:
        giorni = int((query.get("giorni") or ["3"])[0])
        nomi = (query.get("allergie") or query.get("pollini") or [""])[0]
        allergeni = cerca_allergeni(nomi.split(",")) if nomi else None
        trovato = cerca_luogo(luogo)
        try:
            misure = scarica_misure(trovato.latitudine, trovato.longitudine)
        except ErroreAPI:
            misure = {}  # senza misure valgono le stime stagionali
        b = crea_bollettino(trovato, scarica_previsioni(trovato, giorni), allergeni, misure)
    except ValueError as e:
        return 400, {"errore": str(e)}
    except ErroreAPI as e:
        return 502, {"errore": str(e)}
    return 200, b.to_dict()


class Gestore(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/":
            corpo = resources.files(__package__).joinpath("static/index.html").read_bytes()
            self._rispondi(200, corpo, "text/html; charset=utf-8")
        elif url.path == "/api/bollettino":
            stato, dati = bollettino_json(parse_qs(url.query))
            corpo = json.dumps(dati, ensure_ascii=False).encode()
            self._rispondi(stato, corpo, "application/json; charset=utf-8")
        else:
            self._rispondi(404, b"Non trovato", "text/plain; charset=utf-8")

    def _rispondi(self, stato: int, corpo: bytes, tipo: str):
        self.send_response(stato)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers()
        self.wfile.write(corpo)


def avvia(porta: int = 8000):
    server = ThreadingHTTPServer(("127.0.0.1", porta), Gestore)
    print(f"Bollettino allergie su http://127.0.0.1:{porta} (Ctrl+C per uscire)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
