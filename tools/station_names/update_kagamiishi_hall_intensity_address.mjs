import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '0734230';
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '鏡石町不時沼');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '鏡石町役場',
  facilityNameEn: 'Kagamiishi Town Hall',
  publishedAddressJa: '福島県岩瀬郡鏡石町不時沼345',
  placementLocalityJa: '鏡石町不時沼345',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct primary earthquake field-survey host statement and published municipal address',
  note: 'Yuki Sakai research-team 2011 Tohoku earthquake field survey, station page 鏡石町不時沼震度計, explicitly places the meter behind Kagamiishi Town Hall. The original complete station-page text was read using its Japanese character encoding; the parent survey index identifies the visit as 7 April 2011. No new visual inspection of that HTML page\'s photographs is claimed. The municipal September 2025 council record gk_no415_R7.9.pdf, printed page 98 / PDF page 102, records the responsible general-affairs chief\'s answer that the current 1972 hall remains in use with seismic retrofitting and life-extension maintenance; replacement is a future consideration, not a completed move. The complete answer page was text-read and visually inspected. This corroborates building continuity, not a fresh meter inspection. The town\'s current official map/access page identifies the Town Hall and publishes 福島県岩瀬郡鏡石町不時沼345 as its location. The historical northeastern rear/mound description is not asserted as a verified current precise position. No room, floor, installation/transfer date, or source-published seven-digit JMA code is asserted. Placement comes from the explicit named-instrument host account, not coordinates or locality alone. The English facility label is descriptive; approved station names remain unchanged.',
});
for (const url of [
  'http://sakaiy.main.jp/113d.htm',
  'http://sakaiy.main.jp/113/017.htm',
  'https://www.town.kagamiishi.fukushima.jp/gikai/files/2025/12/gk_no415_R7.9.pdf',
  'https://www.town.kagamiishi.fukushima.jp/map/index.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Kagamiishi Town Hall host-address update; protected-field and address/precision checks passed.`);
