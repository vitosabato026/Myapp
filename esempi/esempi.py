"""Esempi d'uso della libreria bollettino_allergie.

    python esempi/esempi.py          # scenari stagionali offline (dati simulati)
    python esempi/esempi.py --live   # aggiunge una consultazione reale via API
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from bollettino_allergie import (Luogo, cerca_luogo, cerca_pollini,  # noqa: E402
                                 crea_bollettino, scarica_pollini)
from bollettino_allergie.cli import formatta  # noqa: E402

DATI = Path(__file__).parent / "dati"


def carica(nome: str):
    dati = json.loads((DATI / f"{nome}.json").read_text())
    return Luogo(**dati.pop("luogo")), dati


def titolo(testo: str):
    print(f"\n{'=' * 64}\n{testo}\n{'=' * 64}")


# Esempio 1: Roma a metà maggio, fioritura dell'olivo e delle graminacee
titolo("Esempio 1 - Roma, maggio: olivo e graminacee")
print(formatta(crea_bollettino(*carica("roma_maggio"))))

# Esempio 2: Milano ad aprile, solo i pollini a cui è allergico l'utente
titolo("Esempio 2 - Milano, aprile: filtro per un allergico alle Betulaceae")
luogo, dati = carica("milano_aprile")
print(formatta(crea_bollettino(luogo, dati, cerca_pollini(["betulla", "ontano"]))))

# Esempio 3: Milano a fine agosto, picco di ambrosia; uso dei dati in un programma
titolo("Esempio 3 - Milano, agosto: ambrosia, giorni da evitare")
b = crea_bollettino(*carica("milano_agosto"), cerca_pollini(["ambrosia"]))
for g in b.giorni:
    r = g.rilevazioni[0]
    stato = "EVITA attività all'aperto" if r.livello == "alto" else "ok con precauzioni"
    print(f"{g.data}: ambrosia {r.media} granuli/m³ ({r.livello}) -> {stato}")

if "--live" in sys.argv:
    # Esempio 4: dati reali di oggi
    titolo("Esempio 4 - Firenze, dati reali (Open-Meteo)")
    luogo = cerca_luogo("Firenze")
    print(formatta(crea_bollettino(luogo, scarica_pollini(luogo, giorni=2))))
