const assert = require('assert');
const { classify } = require('./winet-rssi.js');

const casos = [
  [-24, 'optima'], [-50, 'optima'],
  [-51, 'buena'],  [-60, 'buena'],
  [-61, 'baja'],   [-77, 'baja'],
  [-78, 'debil'],  [-84, 'debil'],
  [-85, 'fuera'],  [-92, 'fuera'],
  [null, 'na'],    [0, 'na'],
  [-127, 'na'],    [-130, 'na'],
];

for (const [rssi, esperado] of casos) {
  const q = classify(rssi).quality;
  assert.strictEqual(q, esperado, `classify(${rssi}) => ${q}, esperado ${esperado}`);
}
console.log('OK: ' + casos.length + ' umbrales de clasificación pasaron');
