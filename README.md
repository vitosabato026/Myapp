# Bollettino Allergie

Consulta il bollettino dei pollini allergenici per una città o località europea:
livelli previsti (fino a 4 giorni) per **graminacee, betulla, ontano, olivo,
artemisia e ambrosia**, con un livello di rischio e un consiglio pratico per ogni giorno.

- Nessuna dipendenza esterna: solo Python 3.10+ e la libreria standard.
- Dati gratuiti senza chiave API: [Open-Meteo](https://open-meteo.com/) (geocoding + Air Quality API,
  modello europeo CAMS/Copernicus). La copertura è limitata all'Europa.
- Tre modi d'uso: riga di comando, interfaccia web, libreria Python.

## Come si usa

### Riga di comando

```bash
python -m bollettino_allergie Milano                          # bollettino a 3 giorni
python -m bollettino_allergie Firenze --giorni 4              # 1-4 giorni
python -m bollettino_allergie Roma -p graminacee,olivo        # solo i pollini che ti interessano
python -m bollettino_allergie Bari --json                     # output JSON
```

Output (Roma, metà maggio):

```
2026-05-19  rischio complessivo: ALTO
  Polline       media   picco  livello
  Graminacee    22.0   39.6  ▅ medio
  Betulla        1.0    1.8  ▂ basso
  Ontano         0.0    0.0  · assente
  Olivo         41.0   73.8  █ alto
  Artemisia      0.0    0.0  · assente
  Ambrosia       0.0    0.0  · assente
  → Rischio alto: evita sforzi all'aperto, tieni chiuse le finestre, ...
```

### Interfaccia web

```bash
python -m bollettino_allergie --web --porta 8000
# apri http://127.0.0.1:8000
```

![Interfaccia web](esempi/screenshot_web.png)

È disponibile anche l'endpoint JSON:
`GET /api/bollettino?luogo=Roma&giorni=3&pollini=graminacee,olivo`

### Libreria

```python
from bollettino_allergie import cerca_luogo, scarica_pollini, crea_bollettino, cerca_pollini

luogo = cerca_luogo("Torino")
b = crea_bollettino(luogo, scarica_pollini(luogo, giorni=3), cerca_pollini(["ambrosia"]))
for giorno in b.giorni:
    print(giorno.data, giorno.livello_massimo, giorno.consiglio)
```

## Esempi

`esempi/esempi.py` mostra quattro casi d'uso:

| # | Scenario | Cosa mostra |
|---|----------|-------------|
| 1 | Roma, maggio | Bollettino completo: olivo **alto**, graminacee da medio ad alto |
| 2 | Milano, aprile | Filtro per un allergico alle Betulaceae: betulla fino a 88 granuli/m³ |
| 3 | Milano, fine agosto | Uso programmatico: individua i giorni con ambrosia alta da evitare |
| 4 | Firenze, oggi | Consultazione reale via API (`--live`) |

```bash
python esempi/esempi.py          # scenari 1-3, offline
python esempi/esempi.py --live   # aggiunge lo scenario 4 con dati reali
```

Gli scenari 1-3 usano dati **simulati** in `esempi/dati/` (stesso formato dell'API,
generati da `esempi/genera_dati.py`), così gli esempi sono significativi anche
fuori stagione, quando i pollini reali sono vicini a zero.

## Come si calcola il livello

Per ogni giorno si fa la media delle concentrazioni orarie (granuli/m³) e la si
confronta con le soglie indicative della rete aerobiologica italiana (AAITO/POLLnet):

| Polline | Basso | Medio | Alto |
|---------|-------|-------|------|
| Graminacee | ≥ 0.6 | ≥ 10 | ≥ 30 |
| Betulla, Ontano | ≥ 0.6 | ≥ 16 | ≥ 50 |
| Olivo, Artemisia, Ambrosia | ≥ 0.6 | ≥ 5 | ≥ 25 |

Il rischio complessivo del giorno è il livello più alto tra i pollini considerati.
I valori sono previsioni modellistiche: il bollettino è informativo e non sostituisce
il parere del medico o dell'allergologo.

## Test

```bash
python -m unittest -v
```

I test non richiedono la rete.
