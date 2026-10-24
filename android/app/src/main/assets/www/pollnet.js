/*
 * Misure della rete di monitoraggio POLLnet (ISPRA): stessa logica di
 * bollettino_allergie/pollnet.py, a cui rimandano i commenti dettagliati.
 * Il server non accetta richieste dal browser (CORS), quindi nell'app i dati
 * vengono scaricati dal codice nativo: la funzione `scarica` è passata dall'esterno.
 */
(function (root) {
  'use strict';

  const WFS_URL = 'https://sdi.isprambiente.it/geoserver/om/ows';
  const DISTANZA_MAX_KM = 60;
  const ETA_MAX_GIORNI = 14;
  const FINESTRA_GIORNI = 7;
  const VALORI_MINIMI = 3;
  const STAZIONI_DA_PROVARE = 3;
  const PARTICELLE_TOTALI = [1, 2]; // per riconoscere i giorni non ancora analizzati

  const arrotonda = (v) => Math.round(v * 10) / 10;
  const giornoIso = (d) => d.toISOString().slice(0, 10);
  const spostaGiorni = (iso, n) => giornoIso(new Date(Date.parse(`${iso}T00:00:00Z`) + n * 864e5));
  const differenzaGiorni = (a, b) => Math.round((Date.parse(`${a}T00:00:00Z`) - Date.parse(`${b}T00:00:00Z`)) / 864e5);

  function distanzaKm(lat1, lon1, lat2, lon2) {
    const r = Math.PI / 180;
    const h = Math.sin(((lat2 - lat1) * r) / 2) ** 2 +
      Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.sin(((lon2 - lon1) * r) / 2) ** 2;
    return 2 * 6371 * Math.asin(Math.sqrt(h));
  }

  function stazioniDaRecord(record) {
    return record.filter((r) => r.LATITUDE && r.LONGITUDE).map((r) => ({
      id: r.STAT_ID, nome: r.STAT_NAME_I, regione: (r.REGI_NAME_I || '').trim(),
      latitudine: Number(r.LATITUDE), longitudine: Number(r.LONGITUDE),
    }));
  }

  function stazioniVicine(stazioni, lat, lon) {
    return stazioni
      .map((s) => [s, distanzaKm(lat, lon, s.latitudine, s.longitudine)])
      .filter(([, d]) => d <= DISTANZA_MAX_KM)
      .sort((a, b) => a[1] - b[1]);
  }

  /** Media degli ultimi 7 giorni misurati per ogni particella; oggi = "AAAA-MM-GG". */
  function misureDaRecord(record, stazione, distanza, oggi) {
    const perParticella = new Map();
    const giorniMisurati = new Set();
    for (const r of record) {
      const giorno = (r.REMA_DATE || '').slice(0, 10);
      if (!giorno || giorno > oggi) continue;
      if (!perParticella.has(r.PART_ID)) perParticella.set(r.PART_ID, new Map());
      perParticella.get(r.PART_ID).set(giorno, r.REMA_CONCENTRATION ?? null);
      if (r.REMA_CONCENTRATION) giorniMisurati.add(giorno);
    }

    const misure = {};
    for (const [partId, valori] of perParticella) {
      if (PARTICELLE_TOTALI.includes(partId)) continue;
      for (const g of valori.keys()) if (!giorniMisurati.has(g)) valori.set(g, null);
      const misurati = [...valori].filter(([, v]) => v != null).map(([g]) => g).sort();
      if (!misurati.length) continue;
      const ultimo = misurati[misurati.length - 1];
      if (differenzaGiorni(oggi, ultimo) > ETA_MAX_GIORNI) continue;
      const finestra = Array.from({ length: FINESTRA_GIORNI }, (_, i) => spostaGiorni(ultimo, i - FINESTRA_GIORNI + 1));
      const serie = finestra.map((g) => [g, valori.get(g) ?? null]);
      const numeri = serie.map(([, v]) => v).filter((v) => v != null);
      if (numeri.length < VALORI_MINIMI) continue;
      misure[partId] = {
        stazione: stazione.nome, distanza_km: arrotonda(distanza), dal: finestra[0], al: ultimo,
        media: arrotonda(numeri.reduce((a, b) => a + b, 0) / numeri.length),
        picco: arrotonda(Math.max(...numeri)), serie,
      };
    }
    return misure;
  }

  /** Somma le particelle che compongono un allergene (es. Carpinus + Ostrya). */
  function combina(misure, particelle) {
    const parti = particelle.map((p) => misure[p]);
    if (!parti.length || parti.some((m) => !m)) return null;
    if (parti.length === 1) return parti[0];
    const [base] = parti;
    return {
      ...base,
      media: arrotonda(parti.reduce((a, m) => a + m.media, 0)),
      picco: arrotonda(parti.reduce((a, m) => a + m.picco, 0)),
      serie: base.serie.map(([g], i) => [g, parti.some((m) => m.serie[i][1] == null) ? null
        : parti.reduce((a, m) => a + m.serie[i][1], 0)]),
    };
  }

  function urlWfs(tipo, filtro, campi) {
    const params = {
      service: 'WFS', version: '2.0.0', request: 'GetFeature',
      typeName: `om:${tipo}`, outputFormat: 'application/json',
    };
    if (filtro) params.cql_filter = filtro;
    if (campi) params.propertyName = campi;
    return `${WFS_URL}?${new URLSearchParams(params)}`;
  }

  function filtroStazione(stazioneId, particelle, oggi) {
    const dal = spostaGiorni(oggi, -(ETA_MAX_GIORNI + FINESTRA_GIORNI));
    const tutte = [...new Set([...particelle, ...PARTICELLE_TOTALI])].sort((a, b) => a - b);
    return `STAT_ID=${stazioneId} AND REMA_DATE>'${dal}' AND REMA_DATE<='${oggi}' AND PART_ID IN (${tutte.join(',')})`;
  }

  const proprieta = (json) => (json.features || []).map((f) => f.properties);

  /**
   * Misure recenti vicino al luogo, per id dell'allergene. `scarica(url)` restituisce
   * una Promise con il JSON; `stazioni` è l'elenco (eventualmente in cache).
   * Se il servizio non risponde restituisce quanto trovato fin lì; se non ha
   * trovato nulla rilancia l'errore, così l'app può dire che le misure mancano
   * per un problema di rete e non per l'assenza di stazioni.
   */
  async function scaricaMisure({ lat, lon, catalogo, stazioni, scarica, oggi = giornoIso(new Date()) }) {
    const daTrovare = catalogo.allergeni.filter((a) => a.pollnet);
    const trovate = {};
    try {
      for (const [stazione, distanza] of stazioniVicine(stazioni, lat, lon).slice(0, STAZIONI_DA_PROVARE)) {
        const mancanti = daTrovare.filter((a) => !trovate[a.id]);
        if (!mancanti.length) break;
        const particelle = [...new Set(mancanti.flatMap((a) => a.pollnet.particelle))];
        const json = await scarica(urlWfs('Concentrazione_pollini_spore',
          filtroStazione(stazione.id, particelle, oggi), 'PART_ID,REMA_DATE,REMA_CONCENTRATION'));
        const misure = misureDaRecord(proprieta(json), stazione, distanza, oggi);
        for (const a of mancanti) {
          const m = combina(misure, a.pollnet.particelle);
          if (m) trovate[a.id] = m;
        }
      }
    } catch (e) {
      if (!Object.keys(trovate).length) throw e;
    }
    return trovate;
  }

  async function scaricaStazioni(scarica) {
    return stazioniDaRecord(proprieta(await scarica(urlWfs('Stazioni_POLLnet'))));
  }

  const api = {
    DISTANZA_MAX_KM, distanzaKm, stazioniDaRecord, stazioniVicine, misureDaRecord, combina,
    filtroStazione, urlWfs, scaricaMisure, scaricaStazioni,
  };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.Pollnet = api;
})(this);
