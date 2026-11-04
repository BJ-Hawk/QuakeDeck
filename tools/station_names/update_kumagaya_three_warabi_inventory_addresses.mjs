import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html';
const updates = [
  ['1120230', '熊谷市大里', 'Kumagaya Osato', 'SIT.073', '熊谷市大里行政センター', 'Kumagaya City Osato Administrative Center', '埼玉県熊谷市中曽根654番地1', 'https://www.city.kumagaya.lg.jp/shisetsu/shiyakusho/annai/oosato.html'],
  ['1120231', '熊谷市妻沼', 'Menuma', 'SIT.075', '熊谷市妻沼行政センター', 'Kumagaya City Menuma Administrative Center', '埼玉県熊谷市弥藤吾2450番地', 'https://www.city.kumagaya.lg.jp/shisetsu/shiyakusho/annai/menuma.html'],
  ['1120232', '熊谷市江南', 'Kumagaya Konan', 'SIT.074', '熊谷市江南行政センター', 'Kumagaya City Konan Administrative Center', '埼玉県熊谷市江南中央1丁目1番地', 'https://www.city.kumagaya.lg.jp/shisetsu/shiyakusho/annai/konan.html'],
  ['1122331', '蕨市中央', 'Chuo', 'SIT.023', '蕨市民会館', 'Warabi Civic Hall', '埼玉県蕨市中央4-21-29', 'https://www.city.warabi.saitama.jp/shisei/soshiki/shisetsu/bunka/1003613.html'],
];
for (const [code, nameJa, english, id, host, facilityNameEn, address, addressSource] of updates) {
  const row = data.stations.find((station) => station.code === code);
  assert.ok(row, `Missing ${code}`);
  assert.equal(row.nameJa, nameJa);
  assert.equal(row.approvedEnglishName, english);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa, `Existing placement ${code}`);
  Object.assign(row, { facilityNameJa: host, facilityNameEn, publishedAddressJa: address,
    placementLocalityJa: address, placementPrecision: 'exact_address',
    metadataStatus: 'Public university installation inventory (2026) and independently published official facility address' });
  const distinction = code === '1122331'
    ? 'The official Civic Hall guide, updated 2 October 2025, publishes Chuo 4-21-29. The inventory explicitly identifies Civic Hall, not City Hall at Chuo 5-14-15; the hall\'s former temporary-office use is not treated as an instrument transfer.'
    : 'The official municipal facility guide independently names the selected administrative center and publishes its address. The observation label refers to the former municipality/administrative district; the host\'s more specific published street locality is retained without changing that approved station label. The main Kumagaya City Hall footer address in Miyacho is not substituted.';
  row.note += ` Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Saitama observation inventory explicitly names ${host} in the installation-facility column for ${id}; its heading states last updated 16 May 2026. The complete selected row and column headings were read without logging in or obtaining restricted waveform data. ${distinction} The published host-site address is recorded as ${address}. Matching uses the explicit host identity and its independently published locality/district, not coordinates, proximity, generic municipal-office policy or a claimed source-printed seven-digit JMA-code crosswalk. The inventory page date is provenance, not an individual physical instrument inspection or full operational-continuity recertification. No room, floor, exact within-site sensor position, installation/transfer date or physical visit is asserted. English facility names are descriptive; approved Japanese/English station names, coordinates and prior provenance remain unchanged.`;
  for (const url of [inventory, addressSource]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}
const codes = new Set(updates.map(([code]) => code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(codes.size, 4);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((station) => station.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  if (!codes.has(row.code)) assert.deepEqual(row, old);
  else {
    assert.deepEqual(Object.keys(row), Object.keys(old));
    for (const key of Object.keys(old)) if (!allowed.has(key)) assert.deepEqual(row[key], old[key]);
    assert.ok(row.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} three Kumagaya center addresses and Warabi Civic Hall; protected fields, other records, order and provenance preserved; zero address/precision mismatches. Coverage delta only: exact +4, municipality/ward -4, remaining facility/address research -4; facility-only unchanged.`);
