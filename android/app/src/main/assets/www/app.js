/*
 * Interfaccia dell'app. La posizione arriva dal servizio di localizzazione del
 * telefono tramite il ponte nativo window.AndroidPosizione (vedi MainActivity.kt);
 * in un normale browser si usa navigator.geolocation.
 */
(function () {
  'use strict';

  const B = window.Bollettino;
  const GEOCODING_URL = 'https://geocoding-api.open-meteo.com/v1/search';
  const QUALITA_ARIA_URL = 'https://air-quality-api.open-meteo.com/v1/air-quality';
  const NOME_LUOGO_URL = 'https://api.bigdatacloud.net/data/reverse-geocode-client';

  const $ = (id) => document.getElementById(id);
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  const memoria = {
    leggi(chiave, predefinito) {
      try { return JSON.parse(localStorage.getItem(chiave)) ?? predefinito; } catch { return predefinito; }
    },
    scrivi(chiave, valore) {
      try { localStorage.setItem(chiave, JSON.stringify(valore)); } catch { /* memoria non disponibile */ }
    },
  };

  let catalogo = null;
  let selezionati = []; // id degli allergeni scelti; [] = tutti
  let ultimoLuogo = null;
  let ultimiDati = null;

  async function getJson(url, params) {
    const r = await fetch(params ? `${url}?${new URLSearchParams(params)}` : url);
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.error) throw new Error(d.reason || `errore ${r.status}`);
    return d;
  }

  async function nomeDaCoordinate(lat, lon) {
    try {
      const d = await getJson(NOME_LUOGO_URL, { latitude: lat, longitude: lon, localityLanguage: 'it' });
      return [d.city || d.locality, d.principalSubdivision, d.countryName].filter(Boolean).join(', ');
    } catch {
      return null; // il nome è solo un aiuto: senza, mostriamo le coordinate
    }
  }

  async function cercaLuogo(nome) {
    const d = await getJson(GEOCODING_URL, { name: nome, count: 1, language: 'it' });
    const r = (d.results || [])[0];
    if (!r) throw new Error(`Nessun luogo trovato per "${nome}"`);
    return {
      lat: r.latitude, lon: r.longitude, quota: r.elevation,
      nome: [r.name, r.admin1, r.country].filter(Boolean).join(', '),
    };
  }

  async function caricaBollettino(luogo) {
    ultimoLuogo = luogo;
    stato('Caricamento del bollettino…');
    try {
      ultimiDati = await getJson(QUALITA_ARIA_URL, {
        latitude: luogo.lat, longitude: luogo.lon,
        hourly: catalogo.allergeni.filter((a) => a.variabile).map((a) => a.variabile).join(','),
        forecast_days: $('giorni').value, timezone: 'auto',
      });
      memoria.scrivi('ultimoLuogo', { lat: luogo.lat, lon: luogo.lon, quota: luogo.quota, nome: luogo.nome });
      mostra();
    } catch (e) {
      stato(`Impossibile scaricare il bollettino: ${e.message}. Controlla la connessione.`, true);
    }
  }

  // --- visualizzazione ---

  function riga(r) {
    const w = r.valore == null ? 0 : Math.min(100, (r.valore / r.soglie[2]) * 100);
    const stagionale = r.tipo === 'stagionale';
    return `<div class="riga">
      <span class="nome">${esc(r.nome)}${stagionale ? '' : `<small>${esc(r.unita)}</small>`}</span>
      <div class="barra${stagionale ? ' stima' : ''}"><span class="${r.livello}" style="width:${w}%"></span></div>
      <span class="val">${stagionale ? '<em>stima</em>' : r.valore.toFixed(1)}<small>${r.livello}</small></span></div>`;
  }

  function sezione(titolo, gruppo) {
    const presenti = gruppo.filter((r) => r.livello && r.livello !== 'assente');
    const assenti = gruppo.filter((r) => r.livello === 'assente').map((r) => r.nome);
    const nd = gruppo.filter((r) => !r.livello).map((r) => r.nome);
    return `<div class="categoria"><h3>${esc(titolo)}</h3>
      ${presenti.map(riga).join('')}
      ${assenti.length ? `<p class="riassunto"><b>Assenti:</b> ${esc(assenti.join(', '))}</p>` : ''}
      ${nd.length ? `<p class="riassunto"><b>Non disponibili:</b> ${esc(nd.join(', '))}</p>` : ''}
    </div>`;
  }

  function mostra() {
    if (!ultimoLuogo || !ultimiDati) return;
    const giorni = B.creaBollettino(ultimiDati, catalogo, {
      ids: selezionati, latitudine: ultimoLuogo.lat, quota: ultimoLuogo.quota,
    });
    $('luogo-nome').textContent = ultimoLuogo.nome ||
      `${ultimoLuogo.lat.toFixed(3)}, ${ultimoLuogo.lon.toFixed(3)}`;
    $('luogo-fonte').textContent = ultimoLuogo.daGps ? 'Posizione attuale'
      : ultimoLuogo.ripiego ? 'Ultima località (posizione non disponibile)' : 'Località cercata';
    $('stato').hidden = true;
    if (!giorni.length || giorni.every((g) => !g.livelloMassimo)) {
      $('risultati').innerHTML = '<p class="vuoto">Dati non disponibili per questa zona: pollini e ' +
        'spore sono coperti solo in Europa.</p>';
      return;
    }
    $('risultati').innerHTML = giorni.map((g) => `
      <section class="giorno">
        <h2>${esc(nomeGiorno(g.data))}
          <span class="badge ${g.livelloMassimo || 'assente'}">${g.livelloMassimo || 'n.d.'}</span></h2>
        <p class="consiglio">${esc(g.consiglio)}</p>
        ${Object.entries(catalogo.categorie).map(([id, titolo]) => {
          const gruppo = g.allergeni.filter((r) => r.categoria === id);
          return gruppo.length ? sezione(titolo, gruppo) : '';
        }).join('')}
      </section>`).join('');
  }

  function nomeGiorno(data) {
    const d = new Date(`${data}T12:00`);
    const oggi = new Date();
    const diff = Math.round((new Date(oggi.toDateString()) - new Date(d.toDateString())) / 864e5);
    const testo = d.toLocaleDateString('it-IT', { weekday: 'long', day: 'numeric', month: 'long' });
    return diff === 0 ? `Oggi, ${testo}` : diff === -1 ? `Domani, ${testo}` : testo;
  }

  function stato(testo, errore = false) {
    const el = $('stato');
    el.hidden = false;
    el.textContent = testo;
    el.classList.toggle('errore', errore);
  }

  // --- posizione del telefono ---

  function usaPosizione() {
    stato('Rilevamento della posizione…');
    if (window.AndroidPosizione) {
      window.AndroidPosizione.richiediPosizione(); // risponde con posizioneRicevuta / posizioneErrore
    } else if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (p) => window.posizioneRicevuta(p.coords.latitude, p.coords.longitude, p.coords.accuracy),
        (e) => window.posizioneErrore(e.message || 'Posizione non disponibile'),
        { enableHighAccuracy: false, timeout: 30000, maximumAge: 15 * 60000 });
    } else {
      window.posizioneErrore('Geolocalizzazione non supportata: cerca una città.');
    }
  }

  window.posizioneRicevuta = async function (lat, lon) {
    stato('Posizione trovata, cerco il nome della località…');
    const nome = await nomeDaCoordinate(lat, lon);
    caricaBollettino({ lat, lon, nome, daGps: true });
  };

  window.posizioneErrore = function (messaggio) {
    // Se non c'è ancora nulla da mostrare, ripiega sull'ultima località consultata
    const salvato = memoria.leggi('ultimoLuogo', null);
    if (!ultimoLuogo && salvato) {
      caricaBollettino({ ...salvato, daGps: false, ripiego: true });
    } else {
      stato(messaggio, true);
    }
  };

  // --- filtri e ricerca ---

  function caricaSelezione() {
    // la versione 1.0 salvava le variabili dell'API (es. "grass_pollen") sotto la chiave "pollini"
    const vecchi = memoria.leggi('pollini', []);
    const salvati = memoria.leggi('allergie', null) ?? vecchi;
    return catalogo.allergeni
      .filter((a) => salvati.includes(a.id) || (a.variabile && salvati.includes(a.variabile)))
      .map((a) => a.id);
  }

  function disegnaFiltri() {
    $('filtri').innerHTML = Object.entries(catalogo.categorie).map(([id, titolo]) => `
      <p class="gruppo">${esc(titolo)}</p>
      <div class="chips">${catalogo.allergeni.filter((a) => a.categoria === id).map((a) => `
        <label class="chip" title="${esc(`${a.famiglia} · ${a.stagione}`)}"><input type="checkbox" value="${a.id}"
          ${selezionati.includes(a.id) ? 'checked' : ''}><span>${esc(a.nome)}${a.tipo === 'stagionale' ? '<sup>*</sup>' : ''}</span></label>`).join('')}
      </div>`).join('');
    $('filtri').addEventListener('change', () => {
      selezionati = [...$('filtri').querySelectorAll('input:checked')].map((i) => i.value);
      memoria.scrivi('allergie', selezionati);
      mostra();
    });
  }

  $('cerca').addEventListener('submit', async (e) => {
    e.preventDefault();
    const nome = $('testo-luogo').value.trim();
    if (!nome || !catalogo) return;
    $('testo-luogo').blur();
    stato(`Cerco "${nome}"…`);
    try {
      caricaBollettino(await cercaLuogo(nome));
    } catch (err) {
      stato(err.message, true);
    }
  });
  $('gps').addEventListener('click', () => catalogo && usaPosizione());
  $('giorni').addEventListener('change', () => ultimoLuogo && caricaBollettino(ultimoLuogo));

  getJson('allergeni.json')
    .then((json) => {
      catalogo = B.preparaCatalogo(json);
      selezionati = caricaSelezione();
      disegnaFiltri();
      usaPosizione(); // all'avvio: bollettino della posizione attuale
    })
    .catch((e) => stato(`Catalogo degli allergeni non disponibile: ${e.message}`, true));
})();
