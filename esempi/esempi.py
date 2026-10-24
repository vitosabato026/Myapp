"""Esempi d'uso della libreria bollettino_allergie.

    python esempi/esempi.py          # scenari stagionali offline (dati simulati)
    python esempi/esempi.py --live   # aggiunge una consultazione reale via API
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from bollettino_allergie import (Luogo, cerca_allergeni, cerca_luogo,  # noqa: E402
                                 crea_bollettino, scarica_previsioni)
from bollettino_allergie.cli import formatta  # noqa: E402

DATI = Path(__file__).parent / "dati"


def carica(nome: str):
    dati = json.loads((DATI / f"{nome}.json").read_text())
    return Luogo(**dati.pop("luogo")), dati


def titolo(testo: str):
    print(f"\n{'=' * 72}\n{testo}\n{'=' * 72}")


# Esempio 1: Roma a metà maggio, bollettino completo di tutte le categorie:
# pollini previsti (olivo, graminacee), stime stagionali (quercia, parietaria...),
# spore e un episodio di polvere sahariana
titolo("Esempio 1 - Roma, maggio: tutti gli allergeni")
print(formatta(crea_bollettino(*carica("roma_maggio"))))

# Esempio 2: Milano ad aprile, solo le allergie di una persona sensibile
# a Betulaceae, cipresso e platano, che soffre anche di asma (PM10, NO2)
titolo("Esempio 2 - Milano, aprile: profilo personale")
luogo, dati = carica("milano_aprile")
profilo = cerca_allergeni(["betulla", "ontano", "carpino", "cipresso", "platano", "pm10", "no2"])
print(formatta(crea_bollettino(luogo, dati, profilo), completo=True))

# Esempio 3: Milano a fine agosto, uso dei dati in un programma: giorni da evitare
titolo("Esempio 3 - Milano, agosto: ambrosia, muffe e ozono")
b = crea_bollettino(*carica("milano_agosto"), cerca_allergeni(["ambrosia", "alternaria", "ozono"]))
for g in b.giorni:
    critici = [r.nome for r in g.rilevazioni if r.livello == "alto"]
    stato = f"EVITA attività all'aperto ({', '.join(critici)})" if critici else "ok con precauzioni"
    print(f"{g.data}: {stato}")

if "--live" in sys.argv:
    # Esempio 4: dati reali di oggi
    titolo("Esempio 4 - Firenze, dati reali (Open-Meteo)")
    luogo = cerca_luogo("Firenze")
    print(formatta(crea_bollettino(luogo, scarica_previsioni(luogo, giorni=2))))
