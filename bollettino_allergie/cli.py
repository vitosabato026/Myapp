"""Interfaccia a riga di comando.

Esempi:
    python -m bollettino_allergie Milano
    python -m bollettino_allergie "Firenze" --giorni 4 --pollini graminacee,olivo
    python -m bollettino_allergie Bari --json
    python -m bollettino_allergie --web --porta 8000
"""

import argparse
import json
import sys

from .api import ErroreAPI, cerca_luogo, scarica_pollini
from .bollettino import Bollettino, crea_bollettino
from .pollini import cerca_pollini

SIMBOLI = {"assente": "·", "basso": "▂", "medio": "▅", "alto": "█", None: "?"}


def _valore(v: float | None) -> str:
    return "  n.d." if v is None else f"{v:6.1f}"


def formatta(b: Bollettino) -> str:
    righe = [f"Bollettino pollini - {b.luogo.descrizione}",
             f"({b.luogo.latitudine:.2f}, {b.luogo.longitudine:.2f}) · "
             "valori in granuli/m³ · fonte: Open-Meteo / CAMS", ""]
    for g in b.giorni:
        righe.append(f"{g.data}  rischio complessivo: {(g.livello_massimo or 'n.d.').upper()}")
        righe.append(f"  {'Polline':<12}{'media':>7}{'picco':>8}  livello")
        for r in g.rilevazioni:
            righe.append(f"  {r.polline:<12}{_valore(r.media)} {_valore(r.picco)}"
                         f"  {SIMBOLI[r.livello]} {r.livello or 'n.d.'}")
        righe.append(f"  → {g.consiglio}")
        righe.append("")
    return "\n".join(righe).rstrip()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="bollettino_allergie",
        description="Consulta il bollettino dei pollini allergenici per un luogo.")
    parser.add_argument("luogo", nargs="?", help="città o località (es. Roma)")
    parser.add_argument("-g", "--giorni", type=int, default=3,
                        help="giorni di previsione, da 1 a 4 (default 3)")
    parser.add_argument("-p", "--pollini",
                        help="filtra i pollini, separati da virgola (es. graminacee,betulla)")
    parser.add_argument("--json", action="store_true", help="output in formato JSON")
    parser.add_argument("--web", action="store_true", help="avvia l'interfaccia web")
    parser.add_argument("--porta", type=int, default=8000, help="porta del server web")
    args = parser.parse_args(argv)

    if args.web:
        from .web import avvia
        avvia(args.porta)
        return 0
    if not args.luogo:
        parser.error("indica un luogo oppure usa --web")

    try:
        pollini = cerca_pollini(args.pollini.split(",")) if args.pollini else None
        luogo = cerca_luogo(args.luogo)
        bollettino = crea_bollettino(luogo, scarica_pollini(luogo, args.giorni), pollini)
    except (ErroreAPI, ValueError) as e:
        print(f"Errore: {e}", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps(bollettino.to_dict(), ensure_ascii=False, indent=2))
    else:
        print(formatta(bollettino))
    return 0
