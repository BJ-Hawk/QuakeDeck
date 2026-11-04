import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '0936430';
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '0936400').map((row) => row.code), [code]);
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '野木町丸林');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '野木町役場',
  facilityNameEn: 'Nogi Town Hall',
  publishedAddressJa: '栃木県下都賀郡野木町大字丸林571番地',
  placementLocalityJa: '野木町大字丸林571番地',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct adopted municipal disaster-plan instrument-host statement and current official hall address',
  note: 'Nogi Town adopted March 2023 disaster plan, 1679615868_doc_33_0.pdf, printed page 震災－応急－205 / PDF page 220, explicitly states that the single intensity meter is installed between the Town Hall main building and annex. The full plan was searched; its cover and complete relevant page were text-read, and the complete relevant page visually inspected. The official parent index, updated 24 March 2023, identifies this as the published 2023 plan, not a draft. The official Town Hall guide 野木町役場について, updated 1 April 2026, independently publishes 栃木県下都賀郡野木町大字丸林571番地 in its named facility/location section; the municipal access guide independently corroborates Marubayashi 571. Composite matching uses the explicit Town Hall meter placement, published Marubayashi address and sole bundled Nogi reporting station, not coordinates or proximity. The between-buildings description is retained as a dated 2023 plan statement, not a fresh 2026 inspection or an assertion of a current exact sensor position. No source is claimed to publish the seven-digit code, a room/floor, installation date or physical transfer date. The English facility label is descriptive; approved station names remain unchanged.',
});
for (const url of [
  'https://www.town.nogi.lg.jp/data/doc/1679615868_doc_33_0.pdf',
  'https://www.town.nogi.lg.jp/anzen_anshin/bousai/page000639.html',
  'https://www.town.nogi.lg.jp/cyousei/cyousya_annai/page001313.html',
  'https://www.town.nogi.lg.jp/cyousei/profile/page001655.html',
]) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);

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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Nogi Town Hall exact address; protected-field and address/precision checks passed.`);
