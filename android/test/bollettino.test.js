// Test della logica JS dell'app; usa gli stessi dati d'esempio del pacchetto Python.
// Esecuzione: node --test android/test/*.test.js
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const { creaBollettino, livello, POLLINI } = require('../app/src/main/assets/www/bollettino.js');

const esempio = (nome) => JSON.parse(
  fs.readFileSync(path.join(__dirname, '../../esempi/dati', `${nome}.json`), 'utf8'));

test('soglie delle graminacee', () => {
  const p = POLLINI.find((x) => x.chiave === 'grass_pollen');
  assert.deepStrictEqual([0.2, 0.6, 10, 30].map((v) => livello(p, v)),
    ['assente', 'basso', 'medio', 'alto']);
  assert.strictEqual(livello(p, null), null);
});

test('media e picco giornalieri, valori mancanti', () => {
  const g = creaBollettino({ hourly: {
    time: ['2026-05-01T00:00', '2026-05-01T12:00', '2026-05-02T00:00'],
    olive_pollen: [10, 30, null],
  } }, ['olive_pollen']);
  assert.deepStrictEqual(g.map((x) => x.data), ['2026-05-01', '2026-05-02']);
  assert.deepStrictEqual(g[0].pollini[0], {
    chiave: 'olive_pollen', polline: 'Olivo', media: 20, picco: 30, livello: 'medio' });
  assert.strictEqual(g[1].pollini[0].media, null);
  assert.strictEqual(g[1].consiglio, 'Dati non disponibili.');
});

test('scenario Roma maggio: stessi risultati del pacchetto Python', () => {
  const g = creaBollettino(esempio('roma_maggio'));
  const olivo = g[1].pollini.find((p) => p.polline === 'Olivo');
  assert.strictEqual(olivo.media, 41);
  assert.strictEqual(olivo.livello, 'alto');
  assert.ok(g.every((x) => x.livelloMassimo === 'alto'));
});

test('filtro: Milano aprile solo betulla', () => {
  const g = creaBollettino(esempio('milano_aprile'), ['birch_pollen']);
  assert.deepStrictEqual(g.map((x) => x.livelloMassimo), ['alto', 'alto', 'medio']);
  assert.ok(g.every((x) => x.pollini.length === 1));
});
