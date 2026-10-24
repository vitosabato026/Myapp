"""Esempi d'uso della libreria bollettino_allergie.

    python esempi/esempi.py          # scenari stagionali offline (dati simulati)
    python esempi/esempi.py --live   # aggiunge una consultazione reale via API
"""

import json
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from bollettino_allergie import (ALLERGENI, Luogo, cerca_allergeni, cerca_luogo,  # noqa: E402
                                 crea_bollettino, pollnet, scarica_previsioni)
from bollettino_allergie.cli import formatta, in_evidenza  # noqa: E402

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

# Esempio 4: parietaria misurata dalle stazioni POLLnet (istantanea reale del 24/10/2026).
# Il calendario la darebbe quasi finita a fine ottobre, ma a Bolzano è ancora a livello medio.
titolo("Esempio 4 - Parietaria misurata: Napoli, Bolzano, Roma (POLLnet, 24 ottobre)")
istantanea = json.loads((DATI / "pollnet_24ottobre.json").read_text())
oggi = date.fromisoformat(istantanea["oggi"])
fine_ottobre = {"hourly": {"time": ["2026-10-24T12:00"], "grass_pollen": [0.1]}}
for nome, (lat, lon) in {"Napoli - Via don Bosco": (40.85, 14.27), "Bolzano": (46.5, 11.35),
                         "Roma Tor Vergata": (41.89, 12.51)}.items():
    stazione = next(pollnet.Stazione(**s) for s in istantanea["stazioni"] if s["nome"] == nome)
    misure = pollnet.misure_da_record(istantanea["record"][str(stazione.id)], stazione,
                                      pollnet.distanza_km(lat, lon, stazione.latitudine, stazione.longitudine), oggi)
    per_allergene = {a.id: m for a in ALLERGENI if (m := pollnet.combina(misure, a.pollnet_particelle))}
    b = crea_bollettino(Luogo(nome.split(" ")[0], lat, lon), fine_ottobre, cerca_allergeni(["parietaria"]),
                        per_allergene)
    print(f"{nome.split(' ')[0]} -", "\n".join(in_evidenza(b.giorni[0].rilevazioni)))

if "--live" in sys.argv:
    # Esempio 5: dati reali di oggi, con le misure POLLnet
    titolo("Esempio 5 - Firenze, dati reali (Open-Meteo + POLLnet)")
    luogo = cerca_luogo("Firenze")
    misure = pollnet.scarica_misure(luogo.latitudine, luogo.longitudine)
    print(formatta(crea_bollettino(luogo, scarica_previsioni(luogo, giorni=2), misure=misure)))
