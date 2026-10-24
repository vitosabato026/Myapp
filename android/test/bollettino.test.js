// Test della logica JS dell'app. Usa lo stesso catalogo e gli stessi dati d'esempio
// del pacchetto Python e verifica che i due producano gli stessi livelli.
// Esecuzione: node --test android/test/*.test.js
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const B = require('../app/src/main/assets/www/bollettino.js');

const RADICE = path.join(__dirname, '../..');
const catalogo = B.preparaCatalogo(JSON.parse(
  fs.readFileSync(path.join(RADICE, 'bollettino_allergie/catalogo/allergeni.json'), 'utf8')));
const esempio = (nome) => JSON.parse(fs.readFileSync(path.join(RADICE, 'esempi/dati', `${nome}.json`), 'utf8'));
const trova = (giorno, id) => giorno.allergeni.find((a) => a.id === id);
const perId = (id) => catalogo.allergeni.find((a) => a.id === id);

test('catalogo: valori predefiniti delle stime stagionali', () => {
  const cipresso = perId('cipresso');
  assert.deepStrictEqual(cipresso.soglie, [0.5, 1.5, 2.5]);
  assert.strictEqual(cipresso.unita, 'indice 0-3');
  assert.strictEqual(perId('ozono').metrica, 'picco');
});

test('soglie delle graminacee', () => {
  const p = perId('graminacee');
  assert.deepStrictEqual([0.2, 0.6, 10, 30].map((v) => B.livello(p, v)), ['assente', 'basso', 'medio', 'alto']);
  assert.strictEqual(B.livello(p, null), null);
});

test('calendario e legge di Hopkins', () => {
  assert.strictEqual(B.indiceStagionale(perId('cipresso').calendario, '2026-02-15'), 3);
  assert.strictEqual(B.ritardoFenologico(45, 122), 12);
  assert.strictEqual(B.versoRitardo(280), -1);
  const par = perId('parietaria').calendario;
  assert.ok(B.indiceStagionale(par, '2026-10-24', B.ritardoFenologico(38)) >
    B.indiceStagionale(par, '2026-10-24', B.ritardoFenologico(46)));
});

test('media, picco e valore confrontato con le soglie', () => {
  const [g1, g2] = B.creaBollettino({ hourly: {
    time: ['2026-05-01T00:00', '2026-05-01T12:00', '2026-05-02T00:00'],
    olive_pollen: [10, 30, null], ozone: [60, 140, null],
  } }, catalogo, { ids: ['olivo', 'ozono'], latitudine: 41.9 });
  assert.deepStrictEqual([g1.data, g2.data], ['2026-05-01', '2026-05-02']);
  const ozono = trova(g1, 'ozono');
  assert.deepStrictEqual([ozono.media, ozono.valore, ozono.livello], [100, 140, 'alto']);
  assert.strictEqual(trova(g1, 'olivo').livello, 'medio');
  assert.strictEqual(trova(g2, 'olivo').media, null);
  assert.strictEqual(g2.consiglio, 'Dati non disponibili.');
});

test('fuori Europa: niente stime stagionali, aria disponibile', () => {
  const [g] = B.creaBollettino({ hourly: { time: ['2026-05-01T00:00'], grass_pollen: [null], pm10: [30] } },
    catalogo, { latitudine: 40.7 });
  assert.strictEqual(trova(g, 'cipresso').livello, null);
  assert.strictEqual(trova(g, 'pm10').livello, 'basso');
});

test('stessi risultati del pacchetto Python su tutti gli scenari', () => {
  const script = `
import json, sys
sys.path.insert(0, ${JSON.stringify(RADICE)})
from tests.test_bollettino import carica
from bollettino_allergie import crea_bollettino
out = {}
for nome in ("roma_maggio", "milano_aprile", "milano_agosto"):
    b = crea_bollettino(*carica(nome))
    out[nome] = [[g.data, g.livello_massimo, [[r.id, r.valore, r.livello] for r in g.rilevazioni]] for g in b.giorni]
print(json.dumps(out))`;
  const python = JSON.parse(execFileSync('python3', ['-c', script], { cwd: RADICE, encoding: 'utf8' }));
  for (const [nome, giorniPy] of Object.entries(python)) {
    const dati = esempio(nome);
    const luogo = dati.luogo;
    const giorniJs = B.creaBollettino(dati, catalogo, { latitudine: luogo.latitudine, quota: luogo.quota });
    const js = giorniJs.map((g) => [g.data, g.livelloMassimo, g.allergeni.map((a) => [a.id, a.valore, a.livello])]);
    assert.deepStrictEqual(js, giorniPy, nome);
  }
});

// --- misure POLLnet (istantanea reale del 24 ottobre 2026) ---
const P = require('../app/src/main/assets/www/pollnet.js');
const istantanea = JSON.parse(fs.readFileSync(path.join(RADICE, 'esempi/dati/pollnet_24ottobre.json'), 'utf8'));
const stazione = (nome) => istantanea.stazioni.find((s) => s.nome === nome);
const misureDi = (nome) => P.misureDaRecord(istantanea.record[stazione(nome).id], stazione(nome), 2, istantanea.oggi);

test('POLLnet: parietaria misurata a Napoli', () => {
  const m = misureDi('Napoli - Via don Bosco')[1362];
  assert.deepStrictEqual([m.dal, m.al, m.media, m.picco], ['2026-10-11', '2026-10-17', 7.6, 10]);
  assert.deepStrictEqual(m.serie.map(([, v]) => v), [8, 5, 5, 7, 10, 10, 8]);
});

test('POLLnet: giorni tutti a zero e date future scartati', () => {
  assert.deepStrictEqual(misureDi('Roma Tor Vergata'), {});
  const s = stazione('Napoli - Via don Bosco');
  const record = [...istantanea.record[s.id], { PART_ID: 1362, REMA_DATE: '2026-11-07Z', REMA_CONCENTRATION: 500 }];
  assert.strictEqual(P.misureDaRecord(record, s, 2, istantanea.oggi)[1362].al, '2026-10-17');
});

test('POLLnet: stazioni vicine e filtro', () => {
  const [prima] = P.stazioniVicine(istantanea.stazioni, 41.89, 12.51);
  assert.strictEqual(prima[0].nome, 'Roma Tor Vergata');
  assert.deepStrictEqual(P.stazioniVicine(istantanea.stazioni, 40.7, -74), []);
  assert.match(P.filtroStazione(131, [1362], '2026-10-24'), /PART_ID IN \(1,2,1362\)/);
  assert.strictEqual(P.combina(misureDi('Bolzano'), []), null);
});

test('POLLnet: scaricaMisure passa alla stazione successiva se la prima non ha dati validi', async () => {
  const roma = stazione('Roma Tor Vergata');
  const napoli = stazione('Napoli - Via don Bosco');
  const richieste = [];
  const scarica = async (url) => {
    const filtro = new URL(url).searchParams.get('cql_filter');
    richieste.push(filtro);
    const s = [roma, napoli].find((x) => filtro.startsWith(`STAT_ID=${x.id} `));
    return { features: (istantanea.record[s.id] || []).map((properties) => ({ properties })) };
  };
  // luogo fittizio vicino a entrambe le stazioni (coordinate spostate per il test)
  const stazioni = [{ ...roma, latitudine: 41, longitudine: 13 }, { ...napoli, latitudine: 41.1, longitudine: 13 }];
  const misure = await P.scaricaMisure({ lat: 41, lon: 13, catalogo, stazioni, scarica, oggi: istantanea.oggi });
  assert.strictEqual(richieste.length, 2);
  assert.strictEqual(misure.parietaria.stazione, 'Napoli - Via don Bosco');
  assert.ok(!misure.cladosporium);
  const [g] = B.creaBollettino({ hourly: { time: ['2026-10-24T00:00'], grass_pollen: [0.1] } }, catalogo,
    { ids: ['parietaria', 'cladosporium'], latitudine: 40.85, misure });
  assert.deepStrictEqual([trova(g, 'parietaria').tipo, trova(g, 'parietaria').livello], ['misura', 'basso']);
  assert.deepStrictEqual(trova(g, 'parietaria').soglie, [2, 20, 70]);
  assert.strictEqual(trova(g, 'cladosporium').tipo, 'stagionale');
});

test('POLLnet: stessi risultati del pacchetto Python', () => {
  const script = `
import json, sys
sys.path.insert(0, ${JSON.stringify(RADICE)})
from tests.test_bollettino import carica_pollnet
from bollettino_allergie import pollnet
oggi, stazioni, record = carica_pollnet()
out = {}
for s in stazioni:
    if s.id in record:
        out[s.id] = {str(k): [m.dal, m.al, m.media, m.picco, [list(x) for x in m.serie]]
                     for k, m in pollnet.misure_da_record(record[s.id], s, 2.0, oggi).items()}
print(json.dumps(out))`;
  const python = JSON.parse(execFileSync('python3', ['-c', script], { cwd: RADICE, encoding: 'utf8' }));
  for (const [id, attese] of Object.entries(python)) {
    const s = istantanea.stazioni.find((x) => String(x.id) === id);
    const js = Object.fromEntries(Object.entries(P.misureDaRecord(istantanea.record[id], s, 2, istantanea.oggi))
      .map(([k, m]) => [k, [m.dal, m.al, m.media, m.picco, m.serie]]));
    assert.deepStrictEqual(js, attese, s.nome);
  }
});

test('POLLnet: errore di rete distinto da "nessuna stazione"', async () => {
  const napoli = stazione('Napoli - Via don Bosco');
  const rotta = async () => { throw new Error('rete'); };
  await assert.rejects(P.scaricaMisure({ lat: napoli.latitudine, lon: napoli.longitudine, catalogo,
    stazioni: istantanea.stazioni, scarica: rotta, oggi: istantanea.oggi }));
  assert.deepStrictEqual(await P.scaricaMisure({ lat: 40.7, lon: -74, catalogo,
    stazioni: istantanea.stazioni, scarica: rotta, oggi: istantanea.oggi }), {});
});
