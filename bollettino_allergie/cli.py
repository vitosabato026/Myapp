"""Interfaccia a riga di comando.

Esempi:
    python -m bollettino_allergie Milano
    python -m bollettino_allergie "Firenze" --giorni 4 --allergie graminacee,cipresso,parietaria
    python -m bollettino_allergie Bari --json
    python -m bollettino_allergie Napoli --senza-misure
    python -m bollettino_allergie --elenco
    python -m bollettino_allergie --web --porta 8000
"""

import argparse
import json
import sys

from .api import ErroreAPI, cerca_luogo, scarica_previsioni
from .bollettino import Bollettino, Rilevazione, crea_bollettino
from .catalogo import ALLERGENI, CATEGORIE, cerca_allergeni
from .pollnet import scarica_misure

SIMBOLI = {"assente": "·", "basso": "▂", "medio": "▅", "alto": "█", None: "?"}
SEGNI = {"stagionale": "*", "misura": "°", "previsione": ""}
BARRE = "▁▂▃▄▅▆▇█"


def _valore(v: float | None) -> str:
    return "  n.d." if v is None else f"{v:6.1f}"


def formatta(b: Bollettino, completo: bool = False) -> str:
    """Bollettino leggibile; senza `completo` gli allergeni assenti sono riassunti in una riga."""
    righe = [f"Bollettino allergie - {b.luogo.descrizione}",
             f"({b.luogo.latitudine:.2f}, {b.luogo.longitudine:.2f}) · previsioni Open-Meteo / CAMS"
             " · ° = misura POLLnet dell'ultima settimana · * = stima stagionale (indice 0-3)", ""]
    if b.giorni:
        righe += in_evidenza(b.giorni[0].rilevazioni)
    for g in b.giorni:
        righe.append(f"{g.data}  rischio complessivo: {(g.livello_massimo or 'n.d.').upper()}")
        for categoria, titolo in CATEGORIE.items():
            gruppo = [r for r in g.rilevazioni if r.categoria == categoria]
            if not gruppo:
                continue
            mostrati = [r for r in gruppo if completo or r.livello not in ("assente", None)]
            righe.append(f"  {titolo}")
            for r in mostrati:
                nome = r.nome + SEGNI[r.tipo]
                unita = "" if r.tipo == "stagionale" else r.unita
                righe.append(f"    {nome:<23}{_valore(r.media)} {_valore(r.picco)} {unita:<11}"
                             f"{SIMBOLI[r.livello]} {r.livello or 'n.d.'}")
            if not completo:
                for livello, etichetta in (("assente", "assenti"), (None, "non disponibili")):
                    nomi = [r.nome for r in gruppo if r.livello == livello]
                    if nomi:
                        righe.append(f"    {etichetta}: {', '.join(nomi)}")
        righe.append(f"  → {g.consiglio}")
        righe.append("")
    return "\n".join(righe).rstrip()


def sparkline(valori: list[float | None]) -> str:
    massimo = max((v for v in valori if v is not None), default=0) or 1
    return "".join("·" if v is None else BARRE[round(v / massimo * (len(BARRE) - 1))] for v in valori)


def in_evidenza(rilevazioni: list[Rilevazione]) -> list[str]:
    """Riquadro dedicato alla parietaria, il polline allergenico più diffuso in Italia."""
    r = next((r for r in rilevazioni if r.id == "parietaria"), None)
    if r is None:
        return []
    righe = [f"In evidenza: Parietaria (Urticaceae) - livello {(r.livello or 'n.d.').upper()}"]
    if r.misura:
        m = r.misura
        valori = [v for _, v in m["serie"]]
        righe.append(f"  misurata a {m['stazione']} ({m['distanza_km']:.0f} km), dal {m['dal']} al {m['al']}:"
                     f" media {m['media']} granuli/m³, picco {m['picco']}")
        righe.append(f"  andamento giornaliero  {sparkline(valori)}  "
                     + " ".join("-" if v is None else f"{v:g}" for v in valori))
    elif r.livello:
        righe.append("  nessuna stazione POLLnet con dati recenti entro 60 km: stima dal calendario pollinico")
    else:
        righe.append("  dati non disponibili per questa zona")
    return righe + [""]


def elenco() -> str:
    righe = []
    for categoria, titolo in CATEGORIE.items():
        righe.append(titolo)
        for a in (a for a in ALLERGENI if a.categoria == categoria):
            fonte = ("previsione" if a.tipo == "previsione"
                     else "misura POLLnet o stima" if a.pollnet_particelle else "stima stagionale")
            righe.append(f"  {a.id:<13}{a.nome:<23}{a.famiglia:<29}{a.stagione:<18}{fonte}")
    return "\n".join(righe)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="bollettino_allergie",
        description="Consulta il bollettino di pollini, spore e qualità dell'aria per un luogo.")
    parser.add_argument("luogo", nargs="?", help="città o località (es. Roma)")
    parser.add_argument("-g", "--giorni", type=int, default=3,
                        help="giorni di previsione, da 1 a 4 (default 3)")
    parser.add_argument("-a", "--allergie", "-p", "--pollini", dest="allergie",
                        help="filtra gli allergeni, separati da virgola (es. graminacee,cipresso)")
    parser.add_argument("--completo", action="store_true", help="mostra anche gli allergeni assenti")
    parser.add_argument("--senza-misure", action="store_true",
                        help="non usare le misure delle stazioni POLLnet, solo le stime stagionali")
    parser.add_argument("--json", action="store_true", help="output in formato JSON")
    parser.add_argument("--elenco", action="store_true", help="elenca gli allergeni disponibili")
    parser.add_argument("--web", action="store_true", help="avvia l'interfaccia web")
    parser.add_argument("--porta", type=int, default=8000, help="porta del server web")
    args = parser.parse_args(argv)

    if args.elenco:
        print(elenco())
        return 0
    if args.web:
        from .web import avvia
        avvia(args.porta)
        return 0
    if not args.luogo:
        parser.error("indica un luogo oppure usa --web o --elenco")

    try:
        allergeni = cerca_allergeni(args.allergie.split(",")) if args.allergie else None
        luogo = cerca_luogo(args.luogo)
        misure = {}
        if not args.senza_misure:
            try:
                misure = scarica_misure(luogo.latitudine, luogo.longitudine)
            except ErroreAPI as e:
                print(f"Avviso: {e}. Uso le stime stagionali.", file=sys.stderr)
        bollettino = crea_bollettino(luogo, scarica_previsioni(luogo, args.giorni), allergeni, misure)
    except (ErroreAPI, ValueError) as e:
        print(f"Errore: {e}", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps(bollettino.to_dict(), ensure_ascii=False, indent=2))
    else:
        print(formatta(bollettino, args.completo))
    return 0
