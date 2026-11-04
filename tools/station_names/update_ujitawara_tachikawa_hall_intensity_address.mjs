import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2634431';
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '2634400').map((row) => row.code), [code]);
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '宇治田原町立川');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '宇治田原町役場',
  facilityNameEn: 'Ujitawara Town Hall',
  publishedAddressJa: '京都府綴喜郡宇治田原町大字立川小字坂口18番地の1',
  placementLocalityJa: '綴喜郡宇治田原町大字立川小字坂口18番地の1',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct municipal instrument-host statement, completed seismic-network relocation results and official facility address',
  note: 'Ujitawara General Affairs and Industry Standing Committee minutes dated 20 October 2015, 20151020sousan.pdf, printed/PDF page 71, explicitly record the crisis-management section chief identifying the intensity meter on Town Hall grounds in Araki. The stated intensity six-lower is a drill scenario, not a claimed actual earthquake observation; the meter-host statement is explicit within that explanation. The cover, preceding full speaker/context page 70 and complete relevant page 71 were text-read, and page 71 visually inspected. The fiscal 2020 major-policy results report, R2_syuyonasesaku.pdf, cover and complete printed page 5 / PDF page 7, new-hall environmental-improvement project, records completed relocation from the old to the new hall, explicitly including seismic-intensity information-network equipment among the disaster-system equipment moved. The full results table was text-read and visually inspected; this is an executed-results report, not merely a construction proposal. The official annual-accounts index identifies it as fiscal 2020 results. The current official Town Hall guide, updated 18 August 2023, explicitly publishes 京都府綴喜郡宇治田原町大字立川小字坂口18番地の1. Composite matching uses the earlier explicit meter host, completed specific seismic-network relocation, published Tachikawa new-hall address and sole bundled Ujitawara reporting station. No source is claimed to publish a seven-digit-code-to-parcel inventory, exact physical transfer date, room, floor, current precise within-site sensor position or fresh 2026 meter inspection. The former Araki hall address and the drill school are not assigned to this Tachikawa station. English facility label is descriptive; approved station names remain unchanged.',
});
for (const url of [
  'https://www.town.ujitawara.kyoto.jp/material/files/group/15/20151020sousan.pdf',
  'https://www.town.ujitawara.kyoto.jp/material/files/group/3/R2_syuyonasesaku.pdf',
  'https://www.town.ujitawara.kyoto.jp/soshiki/kikakuzaiseika/zaisei/1/496.html',
  'https://www.town.ujitawara.kyoto.jp/soshiki/yakuba/yakubashokai/518.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Ujitawara Town Hall address; protected-field and address/precision checks passed.`);
