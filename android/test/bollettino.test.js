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
