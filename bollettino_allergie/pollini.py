"""Catalogo dei pollini monitorati e soglie di concentrazione.

Le soglie (granuli/m³, media giornaliera) seguono le classi usate dalla rete
italiana di monitoraggio aerobiologico (AAITO/POLLnet): assente, bassa, media,
alta. Sono indicative e servono a tradurre i valori numerici in un livello
comprensibile.
"""

from dataclasses import dataclass

LIVELLI = ("assente", "basso", "medio", "alto")


@dataclass(frozen=True)
class Polline:
    chiave: str  # variabile dell'API Open-Meteo
    nome: str
    famiglia: str
    stagione: str
    soglie: tuple[float, float, float]  # inizio basso, medio, alto
    alias: tuple[str, ...] = ()

    def livello(self, concentrazione: float | None) -> str | None:
        if concentrazione is None:
            return None
        basso, medio, alto = self.soglie
        if concentrazione >= alto:
            return "alto"
        if concentrazione >= medio:
            return "medio"
        if concentrazione >= basso:
            return "basso"
        return "assente"


POLLINI = (
    Polline("grass_pollen", "Graminacee", "Poaceae", "aprile-luglio",
            (0.6, 10, 30), ("graminacee", "erba", "grass")),
    Polline("birch_pollen", "Betulla", "Betulaceae", "marzo-maggio",
            (0.6, 16, 50), ("betulla", "birch")),
    Polline("alder_pollen", "Ontano", "Betulaceae", "gennaio-aprile",
            (0.6, 16, 50), ("ontano", "alder")),
    Polline("olive_pollen", "Olivo", "Oleaceae", "maggio-giugno",
            (0.6, 5, 25), ("olivo", "olive")),
    Polline("mugwort_pollen", "Artemisia", "Compositae", "luglio-settembre",
            (0.6, 5, 25), ("artemisia", "mugwort")),
    Polline("ragweed_pollen", "Ambrosia", "Compositae", "agosto-ottobre",
            (0.6, 5, 25), ("ambrosia", "ragweed")),
)

PER_CHIAVE = {p.chiave: p for p in POLLINI}


def cerca_pollini(nomi: list[str]) -> list[Polline]:
    """Converte nomi/alias (es. "graminacee", "betulla") nei pollini corrispondenti."""
    trovati = []
    for nome in nomi:
        n = nome.strip().lower()
        polline = next((p for p in POLLINI if n == p.chiave or n in p.alias), None)
        if polline is None:
            validi = ", ".join(p.alias[0] for p in POLLINI)
            raise ValueError(f"Polline sconosciuto: {nome!r}. Valori ammessi: {validi}")
        if polline not in trovati:
            trovati.append(polline)
    return trovati
