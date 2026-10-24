/*
 * Logica del bollettino: livelli, stime stagionali e aggregazione giornaliera.
 * Il catalogo degli allergeni (allergeni.json) è lo stesso del pacchetto Python
 * (bollettino_allergie/catalogo/allergeni.json) e viene copiato nell'app in fase di build.
 * La logica rispecchia bollettino_allergie/catalogo.py e bollettino.py.
 */
(function (root) {
  'use strict';

  const LIVELLI = ['assente', 'basso', 'medio', 'alto'];
  const SOGLIE_STAGIONALI = [0.5, 1.5, 2.5];
  const LATITUDINE_RIFERIMENTO = 43;

  const CONSIGLI = {
    assente: 'Nessun rischio rilevante.',
    basso: 'Rischio basso: solo i soggetti molto sensibili possono avere sintomi.',
    medio: "Rischio medio: limita le attività all'aperto nelle ore centrali e " +
      'tieni a portata di mano la terapia.',
    alto: "Rischio alto: evita sforzi all'aperto, tieni chiuse le finestre, " +
      'usa occhiali da sole e lava viso e capelli rientrando a casa.',
  };
  const CONSIGLIO_ARIA = 'Aria inquinata: polveri e ozono irritano le vie respiratorie e ' +
    "peggiorano asma e rinite, evita attività intense all'aperto.";

  /** Completa le voci del catalogo con i valori predefiniti delle stime stagionali. */
  function preparaCatalogo(json) {
    return {
      categorie: json.categorie,
      allergeni: json.allergeni.map((a) => ({
        unita: 'indice 0-3', metrica: 'media', variabile: null, alias: [],
        ...a,
        soglie: a.soglie || SOGLIE_STAGIONALI,
      })),
    };
  }

  function livello(allergene, valore) {
    return livelloDaSoglie(allergene.soglie, valore);
  }

  function livelloDaSoglie(soglie, valore) {
    if (valore == null) return null;
    const [basso, medio, alto] = soglie;
    if (valore >= alto) return 'alto';
    if (valore >= medio) return 'medio';
    if (valore >= basso) return 'basso';
    return 'assente';
  }

  // (giorno dell'anno, verso): +1 in inverno-primavera-estate, -1 in autunno
  const VERSO = [[0, 1], [181, 1], [227, -1], [304, -1], [335, 1], [367, 1]];

  function versoRitardo(giornoAnno) {
    for (let i = 1; i < VERSO.length; i++) {
      const [g0, v0] = VERSO[i - 1];
      const [g1, v1] = VERSO[i];
      if (giornoAnno <= g1) return v0 + ((v1 - v0) * (giornoAnno - g0)) / (g1 - g0);
    }
    return 1;
  }

  /** Legge di Hopkins: ~4 giorni per grado di latitudine verso nord e ogni 122 m di quota. */
  function ritardoFenologico(latitudine, quota = 0) {
    const r = 4 * (latitudine - LATITUDINE_RIFERIMENTO) + (4 * Math.max(quota || 0, 0)) / 122;
    return Math.max(-45, Math.min(60, r));
  }

  const arrotonda = (v) => Math.round(v * 10) / 10;

  function indiceStagionale(calendario, data, ritardo = 0) {
    const [anno, mese, giorno] = data.split('-').map(Number);
    const giorniAnno = anno % 4 === 0 ? 366 : 365;
    const doy = Math.round((Date.UTC(anno, mese - 1, giorno) - Date.UTC(anno, 0, 0)) / 864e5);
    const g = (((doy - ritardo * versoRitardo(doy)) % giorniAnno) + giorniAnno) % giorniAnno;
    const posizione = (g - 15.2) / (giorniAnno / 12); // 0 = metà gennaio
    const i = Math.floor(posizione);
    const f = posizione - i;
    const m = (k) => calendario[((k % 12) + 12) % 12];
    return arrotonda(m(i) * (1 - f) + m(i + 1) * f);
  }

  /**
   * Aggrega i valori orari della Air Quality API di Open-Meteo in livelli giornalieri.
   * opzioni: { ids: allergeni da includere (default tutti), latitudine, quota,
   *            misure: { id allergene -> misura POLLnet } (vedi pollnet.js) }
   */
  function creaBollettino(dati, catalogo, opzioni = {}) {
    const ids = opzioni.ids || [];
    const misure = opzioni.misure || {};
    const allergeni = ids.length ? catalogo.allergeni.filter((a) => ids.includes(a.id)) : catalogo.allergeni;
    const orari = (dati && dati.hourly) || {};
    const tempi = orari.time || [];

    const valori = new Map(); // data -> { variabile -> [valori] }
    for (const t of tempi) if (!valori.has(t.slice(0, 10))) valori.set(t.slice(0, 10), {});
    for (const a of allergeni) {
      if (!a.variabile) continue;
      (orari[a.variabile] || []).forEach((v, i) => {
        if (v != null && tempi[i]) (valori.get(tempi[i].slice(0, 10))[a.variabile] ||= []).push(v);
      });
    }

    const polliniPrevisti = catalogo.allergeni
      .filter((a) => a.categoria === 'pollini' && a.variabile).map((a) => a.variabile);
    const coperturaEuropea = polliniPrevisti.some((k) => (orari[k] || []).some((v) => v != null));
    const latitudine = opzioni.latitudine ?? dati.latitude ?? LATITUDINE_RIFERIMENTO;
    const ritardo = ritardoFenologico(latitudine, opzioni.quota ?? dati.elevation);

    return [...valori.keys()].sort().map((data) => {
      const rilevazioni = allergeni.map((a) => {
        let media, picco, valore;
        let tipo = a.tipo, unita = a.unita, soglie = a.soglie;
        const misura = misure[a.id] || null;
        if (misura) {
          tipo = 'misura';
          unita = a.categoria === 'spore' ? 'spore/m³' : 'granuli/m³';
          soglie = a.pollnet.soglie;
          media = valore = misura.media;
          picco = misura.picco;
        } else if (a.tipo === 'stagionale') {
          media = valore = coperturaEuropea ? indiceStagionale(a.calendario, data, ritardo) : null;
          picco = null;
        } else {
          const serie = valori.get(data)[a.variabile] || [];
          media = serie.length ? arrotonda(serie.reduce((x, y) => x + y, 0) / serie.length) : null;
          picco = serie.length ? arrotonda(Math.max(...serie)) : null;
          valore = a.metrica === 'picco' ? picco : media;
        }
        return {
          id: a.id, nome: a.nome, categoria: a.categoria, tipo, unita,
          soglie, media, picco, valore, livello: livelloDaSoglie(soglie, valore), misura,
        };
      });
      const livelli = rilevazioni.map((r) => r.livello).filter(Boolean);
      const livelloMassimo = livelli.length
        ? livelli.reduce((a, b) => (LIVELLI.indexOf(b) > LIVELLI.indexOf(a) ? b : a))
        : null;
      let consiglio = CONSIGLI[livelloMassimo] || 'Dati non disponibili.';
      if (rilevazioni.some((r) => r.categoria === 'aria' && ['medio', 'alto'].includes(r.livello))) {
        consiglio += ' ' + CONSIGLIO_ARIA;
      }
      return { data, livelloMassimo, consiglio, allergeni: rilevazioni };
    });
  }

  const api = {
    LIVELLI, CONSIGLI, preparaCatalogo, livello, livelloDaSoglie, versoRitardo, ritardoFenologico,
    indiceStagionale, creaBollettino,
  };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.Bollettino = api;
})(this);
