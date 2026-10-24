/*
 * Logica del bollettino: catalogo dei pollini, soglie e aggregazione giornaliera.
 * È la stessa del pacchetto Python (bollettino_allergie/pollini.py e bollettino.py):
 * se cambi le soglie, aggiornale in entrambi i posti.
 */
(function (root) {
  'use strict';

  const LIVELLI = ['assente', 'basso', 'medio', 'alto'];

  // soglie = inizio dei livelli basso, medio, alto (granuli/m³, media giornaliera, AAITO/POLLnet)
  const POLLINI = [
    { chiave: 'grass_pollen', nome: 'Graminacee', stagione: 'aprile-luglio', soglie: [0.6, 10, 30] },
    { chiave: 'birch_pollen', nome: 'Betulla', stagione: 'marzo-maggio', soglie: [0.6, 16, 50] },
    { chiave: 'alder_pollen', nome: 'Ontano', stagione: 'gennaio-aprile', soglie: [0.6, 16, 50] },
    { chiave: 'olive_pollen', nome: 'Olivo', stagione: 'maggio-giugno', soglie: [0.6, 5, 25] },
    { chiave: 'mugwort_pollen', nome: 'Artemisia', stagione: 'luglio-settembre', soglie: [0.6, 5, 25] },
    { chiave: 'ragweed_pollen', nome: 'Ambrosia', stagione: 'agosto-ottobre', soglie: [0.6, 5, 25] },
  ];

  const CONSIGLI = {
    assente: 'Nessun rischio rilevante.',
    basso: 'Rischio basso: solo i soggetti molto sensibili possono avere sintomi.',
    medio: "Rischio medio: limita le attività all'aperto nelle ore centrali e " +
      'tieni a portata di mano la terapia.',
    alto: "Rischio alto: evita sforzi all'aperto, tieni chiuse le finestre, " +
      'usa occhiali da sole e lava viso e capelli rientrando a casa.',
  };

  function livello(polline, concentrazione) {
    if (concentrazione == null) return null;
    const [basso, medio, alto] = polline.soglie;
    if (concentrazione >= alto) return 'alto';
    if (concentrazione >= medio) return 'medio';
    if (concentrazione >= basso) return 'basso';
    return 'assente';
  }

  const arrotonda = (v) => Math.round(v * 10) / 10;

  /**
   * Aggrega i valori orari della Air Quality API di Open-Meteo in medie e picchi giornalieri.
   * chiavi: elenco opzionale di chiavi dei pollini da includere (default: tutti).
   */
  function creaBollettino(dati, chiavi) {
    const pollini = chiavi && chiavi.length ? POLLINI.filter((p) => chiavi.includes(p.chiave)) : POLLINI;
    const orari = (dati && dati.hourly) || {};
    const tempi = orari.time || [];

    const valori = new Map(); // data -> { chiave -> [valori] }
    for (const p of pollini) {
      const serie = orari[p.chiave] || [];
      tempi.forEach((t, i) => {
        const data = t.slice(0, 10);
        if (!valori.has(data)) valori.set(data, {});
        const v = serie[i];
        if (v != null) (valori.get(data)[p.chiave] ||= []).push(v);
      });
    }

    return [...valori.keys()].sort().map((data) => {
      const rilevazioni = pollini.map((p) => {
        const serie = valori.get(data)[p.chiave] || [];
        const media = serie.length ? arrotonda(serie.reduce((a, b) => a + b, 0) / serie.length) : null;
        const picco = serie.length ? arrotonda(Math.max(...serie)) : null;
        return { chiave: p.chiave, polline: p.nome, media, picco, livello: livello(p, media) };
      });
      const livelli = rilevazioni.map((r) => r.livello).filter(Boolean);
      const livelloMassimo = livelli.length
        ? livelli.reduce((a, b) => (LIVELLI.indexOf(b) > LIVELLI.indexOf(a) ? b : a))
        : null;
      return {
        data,
        livelloMassimo,
        consiglio: CONSIGLI[livelloMassimo] || 'Dati non disponibili.',
        pollini: rilevazioni,
      };
    });
  }

  const api = { LIVELLI, POLLINI, CONSIGLI, livello, creaBollettino };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.Bollettino = api;
})(this);
