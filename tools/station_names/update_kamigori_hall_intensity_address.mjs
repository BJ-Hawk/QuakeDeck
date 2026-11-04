import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2848131';
const s = data.stations.find((row) => row.code === code);
assert.ok(s, `Missing station ${code}`);
assert.equal(s.nameJa, '上郡町大持');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa, `Existing placement ${code}`);
Object.assign(s, {
  facilityNameJa: '上郡町役場',
  facilityNameEn: 'Kamigori Town Hall',
  publishedAddressJa: '兵庫県赤穂郡上郡町大持278番地',
  placementLocalityJa: '上郡町大持278番地',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct JMA station-to-host statement and published municipal facility address',
  note: 'Kobe Local Meteorological Office, 兵庫県の地震活動 August 2020 report, 一口メモ 震度の観測①, printed page 4 / PDF page 5, explicitly states that the Hyogo Prefecture intensity meter 上郡町大持 is installed at Kamigori Town Hall. The caption identifies its Shindo 1 observation in the earthquake of 28 August 2020 at 00:19. The complete relevant page was text-read and visually inspected. Placement comes from the explicit station-name-to-host caption, not the adjacent regional map or catalogue coordinates. The town\'s official Town Hall facility page, updated 7 February 2022 and currently available, identifies 上郡町役場 and publishes 兵庫県赤穂郡上郡町大持278, with the full postal form 兵庫県赤穂郡上郡町大持278番地. This is historical direct placement evidence paired with the published host address, not a fresh 2026 instrument inspection. No particular hall building, room, floor, or precise within-site meter position is asserted. Neither source is claimed to publish the seven-digit JMA code. The English facility label is descriptive, not claimed as an official English title; approved English and Japanese station names remain unchanged.',
});
for (const url of [
  'https://www.jma-net.go.jp/kobe-c/observation-results/kankoubutsu/jishin-pdf/2020_08.pdf',
  'https://www.town.kamigori.hyogo.jp/soshiki/somuka/shisetsuosagasu/940.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Kamigori Town Hall host-address update; protected-field and address/precision checks passed.`);
