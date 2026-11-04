import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '0721233';
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '南相馬市鹿島区西町');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '南相馬市鹿島区役所',
  facilityNameEn: 'Minamisoma City Kashima Ward Office',
  publishedAddressJa: '福島県南相馬市鹿島区西町一丁目1番地',
  placementLocalityJa: '南相馬市鹿島区西町一丁目1番地',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct primary earthquake field-survey host observation and official facility address',
  note: '汐満将史 et al., 強震観測点周辺の被害調査と発生した地震動の性質, Bulletin of JAEE No.47, October 2022, printed page 7 / PDF page 9, section 3.1(4), explicitly identifies 南相馬市鹿島区西町震度計 at Minamisoma Kashima Ward Office. The complete relevant page was text-read and visually inspected; printed page 8 / PDF page 10 photographs 15 and 16 were also inspected and identify the office and instrument separately. The account concerns the 16 March 2022 Fukushima-oki earthquake field survey, not a fresh 2026 inspection. The municipal Kashima Ward Office guide, updated 29 March 2024 and currently available, directly publishes 南相馬市鹿島区西町一丁目1番地; its department contact independently supplies the Fukushima prefecture prefix. The separate main City Hall address in the footer is not used. The reported entrance placement and surrounding mound describe the historical survey, not a newly verified precise position. No room, floor, installation/transfer date, or seven-digit code published in these sources is asserted. Placement comes from the explicit station-to-host account, not map proximity or coordinates. The English facility label is descriptive, not claimed as an official English title; approved station names remain unchanged.',
});
for (const url of [
  'https://www.jaee.gr.jp/jp/wp-content/uploads/2023/10/kaishi47.pdf',
  'https://www.city.minamisoma.lg.jp/portal/sections/31/3110/2/1648.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Kashima Ward Office host-address update; protected-field and address/precision checks passed.`);
