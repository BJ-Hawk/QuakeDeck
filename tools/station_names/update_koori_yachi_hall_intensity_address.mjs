import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '0730131';
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '桑折町谷地');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '桑折町役場',
  facilityNameEn: 'Koori Town Hall',
  publishedAddressJa: '福島県伊達郡桑折町大字谷地字道下22番地7',
  placementLocalityJa: '桑折町大字谷地字道下22番地7',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct primary earthquake field-survey host caption and published municipal address',
  note: 'Kazuo Seo, 東日本大震災から11年目に発生した福島県沖の地震, primary field-survey report hosted by the Architectural Institute of Japan: PDF page 12, appendix concerning the 13 February 2021 earthquake, explicitly captions the Town Hall intensity meter and separately identifies the hall newly relocated one month earlier, distinguishing the former annex. The complete appendix page was text-read and visually inspected; the cover and survey context were read. The host identification is a historical observation, not a fresh 2026 instrument inspection. The current official Town Hall facility guide, updated 5 January 2026, explicitly publishes 福島県伊達郡桑折町大字谷地字道下22番地7. Composite matching pairs the documented hall meter with the Yachi reporting locality; neither source is claimed to publish its seven-digit JMA code. No exact meter-transfer date, current room, floor, or precise within-site position is asserted. The former Higashiosumi office address is not used. Placement is based on the explicit meter-host caption, not catalogue coordinates or proximity. The English facility label is descriptive; approved station names remain unchanged.',
});
for (const url of [
  'https://saigai.aij.or.jp/saigai_info/20220316_fukushima_oki/20220316_fukushima_oki_Seo_Titech.pdf',
  'https://www.town.koori.fukushima.jp/soshiki/1/2384.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Koori Town Hall host-address update; protected-field and address/precision checks passed.`);
