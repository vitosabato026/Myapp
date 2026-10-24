"""Genera i dati d'esempio (formato API Open-Meteo) per scenari stagionali.

I valori sono simulati con un ciclo giornaliero plausibile (picco nel primo
pomeriggio): servono per mostrare l'app quando nella stagione reale i pollini
sono assenti. Uso: python esempi/genera_dati.py
"""

import json
import math
from pathlib import Path

CHIAVI = ("alder_pollen", "birch_pollen", "grass_pollen",
          "mugwort_pollen", "olive_pollen", "ragweed_pollen")

# scenario -> (luogo, primo giorno, {chiave: media giornaliera per ciascun giorno})
SCENARI = {
    "roma_maggio": ({"nome": "Roma", "latitudine": 41.89193, "longitudine": 12.51133,
                     "regione": "Lazio", "paese": "Italia"}, "2026-05-18",
                    {"grass_pollen": [14, 22, 35], "olive_pollen": [28, 41, 19],
                     "birch_pollen": [2, 1, 0.5], "mugwort_pollen": [0, 0, 0.3]}),
    "milano_aprile": ({"nome": "Milano", "latitudine": 45.46427, "longitudine": 9.18951,
                       "regione": "Lombardia", "paese": "Italia"}, "2026-04-08",
                      {"birch_pollen": [62, 88, 31], "alder_pollen": [9, 6, 3],
                       "grass_pollen": [1.5, 3, 4]}),
    "milano_agosto": ({"nome": "Milano", "latitudine": 45.46427, "longitudine": 9.18951,
                       "regione": "Lombardia", "paese": "Italia"}, "2026-08-27",
                      {"ragweed_pollen": [18, 34, 52], "mugwort_pollen": [6, 4, 2],
                       "grass_pollen": [3, 2, 2]}),
}


def profilo(media: float) -> list[float]:
    # ciclo con picco alle 14; i pesi hanno media 1, quindi la media resta quella indicata
    pesi = [1 + 0.8 * math.cos((h - 14) / 24 * 2 * math.pi) for h in range(24)]
    return [round(media * p, 1) for p in pesi]


def genera(primo_giorno: str, medie: dict[str, list[float]]) -> dict:
    anno, mese, giorno = map(int, primo_giorno.split("-"))
    n = len(next(iter(medie.values())))
    tempi = [f"{anno:04d}-{mese:02d}-{giorno + d:02d}T{h:02d}:00"
             for d in range(n) for h in range(24)]
    orari = {"time": tempi}
    for k in CHIAVI:
        orari[k] = [v for m in medie.get(k, [0] * n) for v in profilo(m)]
    return {"timezone": "Europe/Rome", "hourly": orari}


if __name__ == "__main__":
    cartella = Path(__file__).parent / "dati"
    cartella.mkdir(exist_ok=True)
    for nome, (luogo, inizio, medie) in SCENARI.items():
        dati = {"luogo": luogo, **genera(inizio, medie)}
        (cartella / f"{nome}.json").write_text(json.dumps(dati, indent=1) + "\n")
        print("scritto", nome)
