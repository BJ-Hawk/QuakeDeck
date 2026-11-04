import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2821633';
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '2821600').map((row) => row.code), [code]);
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '高砂市荒井町');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '高砂市役所',
  facilityNameEn: 'Takasago City Hall',
  publishedAddressJa: '兵庫県高砂市荒井町千鳥1丁目1番1号',
  placementLocalityJa: '高砂市荒井町千鳥1丁目1番1号',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct municipal disaster-network report of City Hall meter observation and official hall address',
  note: 'Takasago City official 防災ネットたかさご message 【高砂市からのお知らせ：地震への備えを】, updated 6 January 2026, bosai.net announcement 149634, explicitly reports the intensity meter at Takasago City Hall observing maximum intensity two during that morning\'s eastern Shimane earthquake. The complete original municipal message was directly retrieved and read, not merely a search preview or a third-party repost. The municipal 防災ネットたかさご explanation, updated 23 August 2024, independently identifies this as the city\'s disaster-information service and links to bosai.net. The current official hall guide, updated 24 September 2026, explicitly publishes 兵庫県高砂市荒井町千鳥1丁目1番1号 for City Hall, distinguishing the separately addressed south annex at Arai-cho Chidori 2-2-16. This records the supported City Hall site address, not an assertion that the sensor is inside the new main building or a particular wing, floor, room or precise within-site location. The hall guide\'s September 2021 main-building completion is not treated as an instrument-transfer date. Composite matching uses the explicit City Hall meter observation, published Arai-cho address and sole bundled Takasago reporting station; neither source is claimed to publish its seven-digit station code. The dated 2026 operational observation is not represented as a physical sensor inspection. English facility label is descriptive; approved station names remain unchanged.',
});
for (const url of [
  'https://bosai.net/announce/detail?info=149634',
  'https://www.city.takasago.lg.jp/soshikikarasagasu/kikikanrishitsu/anzen_anshin/3/5/2658.html',
  'https://www.city.takasago.lg.jp/soshikikarasagasu/somuka/yakusho_madoguchiannai/6590.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Takasago City Hall address; protected-field and address/precision checks passed.`);
