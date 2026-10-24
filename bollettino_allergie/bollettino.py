"""Costruzione del bollettino: dai dati orari ai livelli giornalieri."""

from collections import defaultdict
from dataclasses import asdict, dataclass, field
from datetime import date

from .api import Luogo
from .catalogo import ALLERGENI, CATEGORIE, LIVELLI, PER_ID, Allergene, ritardo_fenologico

CONSIGLI = {
    "assente": "Nessun rischio rilevante.",
    "basso": "Rischio basso: solo i soggetti molto sensibili possono avere sintomi.",
    "medio": "Rischio medio: limita le attività all'aperto nelle ore centrali e "
             "tieni a portata di mano la terapia.",
    "alto": "Rischio alto: evita sforzi all'aperto, tieni chiuse le finestre, "
            "usa occhiali da sole e lava viso e capelli rientrando a casa.",
}
CONSIGLIO_ARIA = ("Aria inquinata: polveri e ozono irritano le vie respiratorie e "
                  "peggiorano asma e rinite, evita attività intense all'aperto.")


@dataclass
class Rilevazione:
    id: str
    nome: str
    famiglia: str
    categoria: str
    tipo: str
    unita: str
    media: float | None
    picco: float | None
    valore: float | None  # valore confrontato con le soglie (media, picco o indice stagionale)
    livello: str | None


@dataclass
class Giorno:
    data: str
    rilevazioni: list[Rilevazione] = field(default_factory=list)

    @property
    def livello_massimo(self) -> str | None:
        livelli = [r.livello for r in self.rilevazioni if r.livello]
        return max(livelli, key=LIVELLI.index) if livelli else None

    @property
    def consiglio(self) -> str:
        testo = CONSIGLI.get(self.livello_massimo, "Dati non disponibili.")
        if any(r.categoria == "aria" and r.livello in ("medio", "alto") for r in self.rilevazioni):
            testo += " " + CONSIGLIO_ARIA
        return testo


@dataclass
class Bollettino:
    luogo: Luogo
    giorni: list[Giorno]

    def to_dict(self) -> dict:
        return {
            "luogo": {**asdict(self.luogo), "descrizione": self.luogo.descrizione},
            "categorie": CATEGORIE,
            "giorni": [
                {
                    "data": g.data,
                    "livello_massimo": g.livello_massimo,
                    "consiglio": g.consiglio,
                    "allergeni": [{**asdict(r), "soglie": PER_ID[r.id].soglie}
                                  for r in g.rilevazioni],
                }
                for g in self.giorni
            ],
        }


def crea_bollettino(luogo: Luogo, dati: dict,
                    allergeni: list[Allergene] | None = None) -> Bollettino:
    """Aggrega i valori orari dell'API in medie e picchi giornalieri.

    Per le previsioni il livello si basa sulla media giornaliera (sul picco
    orario per ozono e biossido di azoto), come nei bollettini ufficiali.
    Le stime stagionali valgono solo dove il modello dei pollini è disponibile
    (Europa), perché il calendario è tarato sul clima europeo.
    """
    allergeni = allergeni or list(ALLERGENI)
    orari = dati.get("hourly", {})
    tempi = orari.get("time", [])

    valori: dict[str, dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))
    for t in tempi:
        valori[t[:10]]  # crea il giorno anche se i valori mancano
    for a in allergeni:
        if a.variabile:
            for t, v in zip(tempi, orari.get(a.variabile) or []):
                if v is not None:
                    valori[t[:10]][a.variabile].append(v)

    pollini_previsti = [a.variabile for a in ALLERGENI if a.categoria == "pollini" and a.variabile]
    copertura_europea = any(v is not None for k in pollini_previsti for v in orari.get(k) or [])
    ritardo = ritardo_fenologico(luogo.latitudine, luogo.quota)

    giorni = []
    for data in sorted(valori):
        giorno = Giorno(data)
        for a in allergeni:
            if a.tipo == "stagionale":
                indice = (a.indice_stagionale(date.fromisoformat(data), ritardo)
                          if copertura_europea else None)
                media, picco, valore = indice, None, indice
            else:
                serie = valori[data].get(a.variabile, [])
                media = round(sum(serie) / len(serie), 1) if serie else None
                picco = round(max(serie), 1) if serie else None
                valore = picco if a.metrica == "picco" else media
            giorno.rilevazioni.append(Rilevazione(
                a.id, a.nome, a.famiglia, a.categoria, a.tipo, a.unita,
                media, picco, valore, a.livello(valore)))
        giorni.append(giorno)
    return Bollettino(luogo, giorni)
