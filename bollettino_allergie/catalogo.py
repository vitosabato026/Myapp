"""Catalogo degli allergeni, letto da catalogo/allergeni.json (condiviso con l'app Android).

Tre tipi di dato:
- previsione: concentrazioni del modello CAMS (Open-Meteo) per 6 pollini e per
  polveri e inquinanti che peggiorano i sintomi allergici;
- stagionale: per i pollini e le spore che nessun servizio gratuito prevede,
  stima dal calendario pollinico (indice 0-3), adattata a latitudine e quota.

Le soglie (inizio dei livelli basso, medio, alto) sono indicative: AAITO/POLLnet
per i pollini, indice europeo della qualità dell'aria (EAQI) per gli inquinanti.
"""

import json
import math
from dataclasses import dataclass
from datetime import date
from importlib import resources

LIVELLI = ("assente", "basso", "medio", "alto")

# Per le stime stagionali il valore confrontato con le soglie è l'indice 0-3 del calendario
SOGLIE_STAGIONALI = (0.5, 1.5, 2.5)
LATITUDINE_RIFERIMENTO = 43.0  # il calendario si riferisce all'Italia centrale, in pianura


@dataclass(frozen=True)
class Allergene:
    id: str
    nome: str
    famiglia: str
    categoria: str
    tipo: str  # "previsione" o "stagionale"
    stagione: str
    soglie: tuple[float, float, float]
    unita: str = "indice 0-3"
    metrica: str = "media"  # valore giornaliero confrontato con le soglie: "media" o "picco"
    variabile: str | None = None  # variabile dell'API Open-Meteo (solo previsione)
    calendario: tuple[float, ...] | None = None  # 12 valori mensili (solo stagionale)
    alias: tuple[str, ...] = ()

    def livello(self, valore: float | None) -> str | None:
        if valore is None:
            return None
        basso, medio, alto = self.soglie
        if valore >= alto:
            return "alto"
        if valore >= medio:
            return "medio"
        if valore >= basso:
            return "basso"
        return "assente"

    def indice_stagionale(self, giorno: date, ritardo_giorni: float = 0) -> float:
        """Interpola il calendario mensile (valori a metà mese) al giorno indicato."""
        anno = 366 if giorno.year % 4 == 0 else 365
        doy = giorno.timetuple().tm_yday
        g = (doy - ritardo_giorni * verso_ritardo(doy)) % anno
        posizione = (g - 15.2) / (anno / 12)  # 0 = metà gennaio
        i = math.floor(posizione)
        f = posizione - i
        cal = self.calendario
        return round(cal[i % 12] * (1 - f) + cal[(i + 1) % 12] * f, 1)


# (giorno dell'anno, verso): +1 in inverno-primavera-estate, -1 in autunno
_VERSO = ((0, 1), (181, 1), (227, -1), (304, -1), (335, 1), (367, 1))


def verso_ritardo(giorno_anno: int) -> float:
    """In primavera-estate le fioriture ritardano verso nord e in quota; in autunno
    (agosto-novembre) succede il contrario: la stagione finisce prima."""
    for (g0, v0), (g1, v1) in zip(_VERSO, _VERSO[1:]):
        if giorno_anno <= g1:
            return v0 + (v1 - v0) * (giorno_anno - g0) / (g1 - g0)
    return 1.0


def ritardo_fenologico(latitudine: float, quota: float = 0) -> float:
    """Ritardo (giorni) delle fioriture rispetto all'Italia centrale in pianura.

    Legge bioclimatica di Hopkins: circa 4 giorni per grado di latitudine verso
    nord e 4 giorni ogni 122 m di quota. Limitato a -45/+60 giorni.
    """
    ritardo = 4 * (latitudine - LATITUDINE_RIFERIMENTO) + 4 * max(quota, 0) / 122
    return max(-45.0, min(60.0, ritardo))


def _carica() -> tuple[dict[str, str], tuple[Allergene, ...]]:
    testo = resources.files(__package__).joinpath("catalogo/allergeni.json").read_text("utf-8")
    dati = json.loads(testo)
    allergeni = []
    for a in dati["allergeni"]:
        a = dict(a)
        stagionale = a["tipo"] == "stagionale"
        a["soglie"] = tuple(a.get("soglie") or SOGLIE_STAGIONALI)
        a["alias"] = tuple(a.get("alias", ()))
        if stagionale:
            a["calendario"] = tuple(a["calendario"])
        allergeni.append(Allergene(**a))
    return dati["categorie"], tuple(allergeni)


CATEGORIE, ALLERGENI = _carica()
PER_ID = {a.id: a for a in ALLERGENI}
VARIABILI_API = tuple(a.variabile for a in ALLERGENI if a.variabile)


def cerca_allergeni(nomi: list[str]) -> list[Allergene]:
    """Converte nomi, id o alias (es. "graminacee", "cipresso", "pm10") negli allergeni."""
    trovati = []
    for nome in nomi:
        n = nome.strip().lower()
        if not n:
            continue
        allergene = next((a for a in ALLERGENI
                          if n in (a.id, a.nome.lower(), a.variabile) or n in a.alias), None)
        if allergene is None:
            validi = ", ".join(a.id for a in ALLERGENI)
            raise ValueError(f"Allergene sconosciuto: {nome!r}. Valori ammessi: {validi}")
        if allergene not in trovati:
            trovati.append(allergene)
    return trovati
