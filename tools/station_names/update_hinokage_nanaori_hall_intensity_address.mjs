import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '4544232';
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '4544200').map((row) => row.code), [code]);
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '日之影町七折');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '日之影町役場',
  facilityNameEn: 'Hinokage Town Hall',
  publishedAddressJa: '宮崎県西臼杵郡日之影町大字七折9079番地',
  placementLocalityJa: '西臼杵郡日之影町大字七折9079番地',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct prefectural assembly confirmation of completed Town Hall instrument relocation and official published facility address',
  note: 'Miyazaki Prefectural Assembly February 2022 regular-session minutes, 202202_1.pdf, printed page 165 / PDF page 177, meeting dated 28 February 2022, records the crisis-management chief explicitly confirming that Hinokage and Kadogawa had relocated their intensity-meter equipment in connection with their Town Hall moves, that the missing observations at the 22 January earthquake had been due to awaiting JMA approval after relocation, and that approval had since been granted and normal operation resumed. The cover, complete relevant page and following discussion were text-read; the complete relevant page was directly downloaded in memory and visually inspected. This is completed instrument-relocation evidence, not an inference from a hall construction budget or a planned move. The town official Town Hall guide, published 6 May 2021, explicitly identifies the new hall and publishes 宮崎県西臼杵郡日之影町大字七折9079番地 in its body. Composite matching uses the responsible prefectural official\'s completed hall-associated instrument-relocation statement, published Nanaori hall address and sole bundled Hinokage reporting station. Neither source is claimed to publish the seven-digit station code or a precise within-site sensor position. No exact physical move date, installation date, room, floor or fresh 2026 sensor inspection is asserted; the 2021 hall-guide date and February 2022 confirmation are not treated as the physical instrument-transfer date. The old hall\'s address is not used. English facility label is descriptive; approved station names remain unchanged.',
});
for (const url of [
  'https://www.pref.miyazaki.lg.jp/documents/82306/202202_1.pdf',
  'https://www.town.hinokage.lg.jp/docs/2016031600018/',
]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);

const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((row) => row.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  if (row.code !== code) assert.deepEqual(row, old);
  else {
    for (const key of new Set([...Object.keys(row), ...Object.keys(old)])) if (!allowed.has(key)) assert.deepEqual(row[key], old[key]);
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Hinokage Town Hall address; protected-field and address/precision checks passed.`);
