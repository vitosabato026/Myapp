/*
 * Interfaccia dell'app. La posizione arriva dal servizio di localizzazione del
 * telefono tramite il ponte nativo window.AndroidPosizione (vedi MainActivity.kt);
 * in un normale browser si usa navigator.geolocation.
 */
(function () {
  'use strict';

  const { POLLINI } = window.Bollettino;
  const GEOCODING_URL = 'https://geocoding-api.open-meteo.com/v1/search';
  const QUALITA_ARIA_URL = 'https://air-quality-api.open-meteo.com/v1/air-quality';
  const NOME_LUOGO_URL = 'https://api.bigdatacloud.net/data/reverse-geocode-client';
  // valore che riempie la barra: la soglia del livello alto
  const SCALA = Object.fromEntries(POLLINI.map((p) => [p.chiave, p.soglie[2]]));

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

  let selezionati = memoria.leggi('pollini', []); // [] = tutti
  let ultimoLuogo = null;
  let ultimiDati = null;

  async function getJson(url, params) {
    const r = await fetch(`${url}?${new URLSearchParams(params)}`);
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
      lat: r.latitude, lon: r.longitude,
      nome: [r.name, r.admin1, r.country].filter(Boolean).join(', '),
    };
  }

  async function caricaBollettino(luogo) {
    ultimoLuogo = luogo;
    stato('Caricamento del bollettino…');
    try {
      ultimiDati = await getJson(QUALITA_ARIA_URL, {
        latitude: luogo.lat, longitude: luogo.lon,
        hourly: POLLINI.map((p) => p.chiave).join(','),
        forecast_days: $('giorni').value, timezone: 'auto',
      });
      memoria.scrivi('ultimoLuogo', { lat: luogo.lat, lon: luogo.lon, nome: luogo.nome });
      mostra();
    } catch (e) {
      stato(`Impossibile scaricare il bollettino: ${e.message}. Controlla la connessione.`, true);
    }
  }

  function mostra() {
    if (!ultimoLuogo || !ultimiDati) return;
    const giorni = window.Bollettino.creaBollettino(ultimiDati, selezionati);
    $('luogo-nome').textContent = ultimoLuogo.nome ||
      `${ultimoLuogo.lat.toFixed(3)}, ${ultimoLuogo.lon.toFixed(3)}`;
    $('luogo-fonte').textContent = ultimoLuogo.daGps ? 'Posizione attuale'
      : ultimoLuogo.ripiego ? 'Ultima località (posizione non disponibile)' : 'Località cercata';
    $('stato').hidden = true;
    if (!giorni.length || giorni.every((g) => !g.livelloMassimo)) {
      $('risultati').innerHTML = '<p class="vuoto">Dati sui pollini non disponibili per questa zona ' +
        '(il servizio copre solo l\'Europa).</p>';
      return;
    }
    $('risultati').innerHTML = giorni.map((g) => `
      <section class="giorno">
        <h2>${esc(nomeGiorno(g.data))}
          <span class="badge ${g.livelloMassimo || 'assente'}">${g.livelloMassimo || 'n.d.'}</span></h2>
        <p class="consiglio">${esc(g.consiglio)}</p>
        ${g.pollini.map((p) => {
          const w = p.media == null ? 0 : Math.min(100, (p.media / SCALA[p.chiave]) * 100);
          return `<div class="riga"><span>${esc(p.polline)}</span>
            <div class="barra"><span class="${p.livello || 'assente'}" style="width:${w}%"></span></div>
            <span class="val">${p.media == null ? 'n.d.' : p.media.toFixed(1)}<small>${p.livello || ''}</small></span></div>`;
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

  function disegnaFiltri() {
    $('filtri').innerHTML = POLLINI.map((p) => `
      <label class="chip"><input type="checkbox" value="${p.chiave}"
        ${selezionati.includes(p.chiave) ? 'checked' : ''}><span>${esc(p.nome)}</span></label>`).join('');
    $('filtri').addEventListener('change', () => {
      selezionati = [...$('filtri').querySelectorAll('input:checked')].map((i) => i.value);
      memoria.scrivi('pollini', selezionati);
      mostra();
    });
  }

  $('cerca').addEventListener('submit', async (e) => {
    e.preventDefault();
    const nome = $('testo-luogo').value.trim();
    if (!nome) return;
    $('testo-luogo').blur();
    stato(`Cerco "${nome}"…`);
    try {
      caricaBollettino(await cercaLuogo(nome));
    } catch (err) {
      stato(err.message, true);
    }
  });
  $('gps').addEventListener('click', usaPosizione);
  $('giorni').addEventListener('change', () => ultimoLuogo && caricaBollettino(ultimoLuogo));

  disegnaFiltri();
  usaPosizione(); // all'avvio: bollettino della posizione attuale
})();
