"""Client per le API gratuite di Open-Meteo (geocoding e qualità dell'aria).

Non serve alcuna chiave. I dati sui pollini provengono dal modello europeo
CAMS (Copernicus) e sono disponibili solo per l'Europa.
"""

import json
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass

from .pollini import POLLINI

GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
QUALITA_ARIA_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"


class ErroreAPI(Exception):
    pass


@dataclass(frozen=True)
class Luogo:
    nome: str
    latitudine: float
    longitudine: float
    regione: str = ""
    paese: str = ""

    @property
    def descrizione(self) -> str:
        return ", ".join(x for x in (self.nome, self.regione, self.paese) if x)


def _get_json(url: str, params: dict, timeout: float = 15) -> dict:
    query = urllib.parse.urlencode(params)
    try:
        with urllib.request.urlopen(f"{url}?{query}", timeout=timeout) as risposta:
            dati = json.load(risposta)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
        raise ErroreAPI(f"Richiesta a {url} fallita: {e}") from e
    if dati.get("error"):
        raise ErroreAPI(dati.get("reason", "errore sconosciuto"))
    return dati


def cerca_luogo(nome: str) -> Luogo:
    """Restituisce il luogo più rilevante per il nome indicato."""
    dati = _get_json(GEOCODING_URL, {"name": nome, "count": 1, "language": "it"})
    risultati = dati.get("results") or []
    if not risultati:
        raise ErroreAPI(f"Nessun luogo trovato per {nome!r}")
    r = risultati[0]
    return Luogo(r["name"], r["latitude"], r["longitude"],
                 r.get("admin1", ""), r.get("country", ""))


def scarica_pollini(luogo: Luogo, giorni: int = 3) -> dict:
    """Scarica le concentrazioni orarie di polline (granuli/m³) per il luogo."""
    if not 1 <= giorni <= 4:
        raise ValueError("Le previsioni dei pollini coprono da 1 a 4 giorni")
    return _get_json(QUALITA_ARIA_URL, {
        "latitude": luogo.latitudine,
        "longitude": luogo.longitudine,
        "hourly": ",".join(p.chiave for p in POLLINI),
        "forecast_days": giorni,
        "timezone": "auto",
    })
