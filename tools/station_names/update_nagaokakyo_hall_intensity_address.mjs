import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2620931';
const s = data.stations.find((row) => row.code === code);
assert.ok(s, `Missing station ${code}`);
assert.equal(s.nameJa, '長岡京市開田');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa, `Existing placement ${code}`);
Object.assign(s, {
  facilityNameJa: '長岡京市役所',
  facilityNameEn: 'Nagaokakyo City Hall',
  publishedAddressJa: '京都府長岡京市開田1丁目1番1号',
  placementLocalityJa: '長岡京市開田1丁目1番1号',
  placementPrecision: 'exact_address',
  metadataStatus: 'Official current municipal disaster-plan instrument-host statement and published City Hall address',
  note: 'Nagaokakyo City regional disaster plan, earthquake-response volume, part 3 chapter 2, printed page 100 / PDF page 108 of shinsaitaisaku2.pdf, explicitly states under Kyoto Prefecture seismic-intensity information network that the instrumental intensity meter is installed on the City Hall grounds. The complete relevant page was text-read and visually inspected. This is the current PDF linked by the official plan index updated 14 September 2026, not the obsolete 6-2.pdf search-index copy. The date is the index update date, not an inferred plan adoption or instrument-transfer date. The city\'s official City Hall profile directly identifies 長岡京市役所 and publishes 京都府長岡京市開田1丁目1番1号; the current plan index independently retains the same postal address. No particular old/new hall wing, floor, room, or current position within the grounds is asserted during the phased hall reconstruction. A budgeted device relocation is not treated as proof of a completed instrument move. The explicit current plan host statement, not the locality or catalogue coordinates, supports this placement; neither source is claimed to publish the seven-digit JMA code. The English facility label is descriptive, not asserted to be an official English title; approved English and Japanese station names remain unchanged.',
});
for (const url of [
  'https://www.city.nagaokakyo.lg.jp/0000000325.html',
  'https://www.city.nagaokakyo.lg.jp/cmsfiles/contents/0000000/325/shinsaitaisaku2.pdf',
  'https://www.city.nagaokakyo.lg.jp/0000014355.html',
]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((row) => row.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const current = data.stations[i];
  const old = before.stations[i];
  assert.equal(current.code, old.code);
  if (current.code !== code) assert.deepEqual(current, old);
  else {
    for (const key of new Set([...Object.keys(current), ...Object.keys(old)])) if (!allowed.has(key)) assert.deepEqual(current[key], old[key]);
    for (const url of old.sourceUrls) assert.ok(current.sourceUrls.includes(url));
  }
  assert.equal(current.placementPrecision === 'exact_address', Boolean(current.publishedAddressJa), `Address/precision mismatch ${current.code}`);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Nagaokakyo City Hall host-address update; protected-field and address/precision checks passed.`);
