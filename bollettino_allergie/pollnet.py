"""Misure della rete di monitoraggio aerobiologico POLLnet (ISPRA).

Le stazioni POLLnet (circa 60 in Italia) misurano ogni giorno le concentrazioni
di pollini e spore; i dati sono pubblicati con circa una settimana di ritardo
come servizio WFS. Per gli allergeni senza previsione (parietaria, cipresso, ...)
la misura della stazione più vicina è molto più affidabile del calendario.

La scelta e l'aggregazione dei dati sono funzioni pure (misure_da_record), così
l'app Android può applicare la stessa logica (pollnet.js) e i test confrontarle.
"""

import json
import math
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass
from datetime import date, timedelta
from functools import lru_cache

from .api import ErroreAPI
from .catalogo import ALLERGENI

WFS_URL = "https://sdi.isprambiente.it/geoserver/om/ows"
DISTANZA_MAX_KM = 60  # oltre, la stazione non rappresenta più il luogo
ETA_MAX_GIORNI = 14  # dati più vecchi non descrivono la situazione attuale
FINESTRA_GIORNI = 7  # la misura è la media degli ultimi 7 giorni disponibili
VALORI_MINIMI = 3  # giorni misurati necessari nella finestra
STAZIONI_DA_PROVARE = 3
# Pollini totali e spore totali: servono a riconoscere i giorni non ancora analizzati,
# che alcune stazioni pubblicano con tutte le concentrazioni a zero
PARTICELLE_TOTALI = (1, 2)


@dataclass(frozen=True)
class Stazione:
    id: int
    nome: str
    regione: str
    latitudine: float
    longitudine: float


@dataclass
class Misura:
    stazione: str
    distanza_km: float
    dal: str
    al: str
    media: float
    picco: float
    serie: list[tuple[str, float | None]]  # valori giornalieri della finestra

    def to_dict(self) -> dict:
        return asdict(self)


def distanza_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


def stazioni_vicine(stazioni: list[Stazione], lat: float, lon: float) -> list[tuple[Stazione, float]]:
    vicine = [(s, distanza_km(lat, lon, s.latitudine, s.longitudine)) for s in stazioni]
    return sorted((x for x in vicine if x[1] <= DISTANZA_MAX_KM), key=lambda x: x[1])


def misure_da_record(record: list[dict], stazione: Stazione, distanza: float,
                     oggi: date) -> dict[int, Misura]:
    """Media degli ultimi 7 giorni misurati per ogni particella (PART_ID) della stazione.

    record: proprietà WFS con PART_ID, REMA_DATE ("2026-10-17Z") e REMA_CONCENTRATION.
    Scarta le date future (errori di inserimento), i giorni in cui tutte le
    concentrazioni sono zero (giorni non ancora analizzati: un giorno reale ha
    sempre qualche polline o spora) e le particelle con dati troppo vecchi o
    troppo pochi valori.
    """
    per_particella: dict[int, dict[str, float | None]] = {}
    giorni_misurati = set()
    for r in record:
        giorno = (r.get("REMA_DATE") or "")[:10]
        if not giorno or giorno > oggi.isoformat():
            continue
        valore = r.get("REMA_CONCENTRATION")
        per_particella.setdefault(r["PART_ID"], {})[giorno] = valore
        if valore:
            giorni_misurati.add(giorno)
    for valori in per_particella.values():
        for giorno in valori:
            if giorno not in giorni_misurati:
                valori[giorno] = None

    misure = {}
    for part_id, valori in per_particella.items():
        if part_id in PARTICELLE_TOTALI:
            continue
        misurati = [g for g, v in valori.items() if v is not None]
        if not misurati:
            continue
        ultimo = date.fromisoformat(max(misurati))
        if (oggi - ultimo).days > ETA_MAX_GIORNI:
            continue
        finestra = [(ultimo - timedelta(days=i)).isoformat() for i in range(FINESTRA_GIORNI - 1, -1, -1)]
        serie = [(g, valori.get(g)) for g in finestra]
        numeri = [v for _, v in serie if v is not None]
        if len(numeri) < VALORI_MINIMI:
            continue
        misure[part_id] = Misura(stazione.nome, round(distanza, 1), finestra[0], finestra[-1],
                                 round(sum(numeri) / len(numeri), 1), round(max(numeri), 1), serie)
    return misure


def combina(misure: dict[int, Misura], particelle: tuple[int, ...]) -> Misura | None:
    """Somma le particelle che compongono un allergene (es. Carpinus + Ostrya)."""
    parti = [misure[p] for p in particelle if p in misure]
    if not particelle or len(parti) != len(particelle):
        return None
    if len(parti) == 1:
        return parti[0]
    base = parti[0]
    serie = [(g, None if any(m.serie[i][1] is None for m in parti) else sum(m.serie[i][1] for m in parti))
             for i, (g, _) in enumerate(base.serie)]
    return Misura(base.stazione, base.distanza_km, base.dal, base.al,
                  round(sum(m.media for m in parti), 1), round(sum(m.picco for m in parti), 1), serie)


# --- accesso al servizio WFS ---

def _wfs(tipo: str, filtro: str | None = None, campi: str | None = None, timeout: float = 30) -> list[dict]:
    params = {"service": "WFS", "version": "2.0.0", "request": "GetFeature",
              "typeName": f"om:{tipo}", "outputFormat": "application/json"}
    if filtro:
        params["cql_filter"] = filtro
    if campi:
        params["propertyName"] = campi
    try:
        with urllib.request.urlopen(f"{WFS_URL}?{urllib.parse.urlencode(params)}", timeout=timeout) as r:
            dati = json.load(r)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
        raise ErroreAPI(f"Servizio POLLnet non disponibile: {e}") from e
    return [f["properties"] for f in dati.get("features", [])]


def stazioni_da_record(record: list[dict]) -> list[Stazione]:
    return [Stazione(r["STAT_ID"], r["STAT_NAME_I"], (r.get("REGI_NAME_I") or "").strip(),
                     float(r["LATITUDE"]), float(r["LONGITUDE"]))
            for r in record if r.get("LATITUDE") and r.get("LONGITUDE")]


@lru_cache(maxsize=1)
def scarica_stazioni() -> list[Stazione]:
    return stazioni_da_record(_wfs("Stazioni_POLLnet"))


def filtro_stazione(stazione_id: int, particelle: list[int], oggi: date) -> str:
    dal = (oggi - timedelta(days=ETA_MAX_GIORNI + FINESTRA_GIORNI)).isoformat()
    tutte = sorted(set(particelle) | set(PARTICELLE_TOTALI))
    return (f"STAT_ID={stazione_id} AND REMA_DATE>'{dal}' AND REMA_DATE<='{oggi.isoformat()}'"
            f" AND PART_ID IN ({','.join(map(str, tutte))})")


def scarica_misure(lat: float, lon: float, oggi: date | None = None) -> dict[str, Misura]:
    """Misure POLLnet recenti vicino al luogo, per id dell'allergene.

    Prova le stazioni più vicine (entro 60 km) finché tutti gli allergeni hanno
    una misura o le stazioni finiscono. Se il servizio non risponde restituisce
    quanto trovato fin lì; se non ha trovato nulla solleva ErroreAPI, così chi
    chiama può distinguere un problema di rete dall'assenza di stazioni.
    """
    oggi = oggi or date.today()
    da_trovare = {a.id: a.pollnet_particelle for a in ALLERGENI if a.pollnet_particelle}
    trovate: dict[str, Misura] = {}
    try:
        vicine = stazioni_vicine(scarica_stazioni(), lat, lon)[:STAZIONI_DA_PROVARE]
        for stazione, distanza in vicine:
            particelle = sorted({p for id_, parti in da_trovare.items() if id_ not in trovate for p in parti})
            if not particelle:
                break
            record = _wfs("Concentrazione_pollini_spore", filtro_stazione(stazione.id, particelle, oggi),
                          "PART_ID,REMA_DATE,REMA_CONCENTRATION")
            misure = misure_da_record(record, stazione, distanza, oggi)
            for id_, parti in da_trovare.items():
                if id_ not in trovate and (m := combina(misure, parti)):
                    trovate[id_] = m
    except ErroreAPI:
        if not trovate:
            raise
    return trovate
