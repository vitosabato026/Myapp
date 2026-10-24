"""Costruzione del bollettino: dai dati orari ai livelli giornalieri."""

from collections import defaultdict
from dataclasses import asdict, dataclass, field

from .api import Luogo
from .pollini import LIVELLI, POLLINI, Polline

CONSIGLI = {
    "assente": "Nessun rischio rilevante.",
    "basso": "Rischio basso: solo i soggetti molto sensibili possono avere sintomi.",
    "medio": "Rischio medio: limita le attività all'aperto nelle ore centrali e "
             "tieni a portata di mano la terapia.",
    "alto": "Rischio alto: evita sforzi all'aperto, tieni chiuse le finestre, "
            "usa occhiali da sole e lava viso e capelli rientrando a casa.",
}


@dataclass
class Rilevazione:
    polline: str
    famiglia: str
    media: float | None
    picco: float | None
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
        return CONSIGLI.get(self.livello_massimo, "Dati non disponibili.")


@dataclass
class Bollettino:
    luogo: Luogo
    giorni: list[Giorno]

    def to_dict(self) -> dict:
        return {
            "luogo": {**asdict(self.luogo), "descrizione": self.luogo.descrizione},
            "giorni": [
                {
                    "data": g.data,
                    "livello_massimo": g.livello_massimo,
                    "consiglio": g.consiglio,
                    "pollini": [asdict(r) for r in g.rilevazioni],
                }
                for g in self.giorni
            ],
        }


def crea_bollettino(luogo: Luogo, dati: dict,
                    pollini: list[Polline] | None = None) -> Bollettino:
    """Aggrega i valori orari dell'API in medie e picchi giornalieri.

    Il livello di rischio si basa sulla media giornaliera, come nei bollettini
    aerobiologici ufficiali; il picco orario è riportato per completezza.
    """
    pollini = pollini or list(POLLINI)
    orari = dati.get("hourly", {})
    tempi = orari.get("time", [])

    valori: dict[str, dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))
    for chiave in (p.chiave for p in pollini):
        for t, v in zip(tempi, orari.get(chiave) or []):
            giorno = valori[t[:10]]  # crea il giorno anche se il valore manca
            if v is not None:
                giorno[chiave].append(v)

    giorni = []
    for data in sorted(valori):
        giorno = Giorno(data)
        for p in pollini:
            serie = valori[data].get(p.chiave, [])
            media = round(sum(serie) / len(serie), 1) if serie else None
            picco = round(max(serie), 1) if serie else None
            giorno.rilevazioni.append(
                Rilevazione(p.nome, p.famiglia, media, picco, p.livello(media)))
        giorni.append(giorno)
    return Bollettino(luogo, giorni)

