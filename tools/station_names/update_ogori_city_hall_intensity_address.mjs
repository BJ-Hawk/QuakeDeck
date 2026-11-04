import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '4021630';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '小郡市小郡');
assert.equal(station.municipalityCode, '4021600');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(data.stations.filter((row) => row.municipalityCode === '4021600').length, 1);
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '小郡市役所',
  facilityNameEn: 'Ogori City Hall',
  publishedAddressJa: '福岡県小郡市小郡255番地1',
  placementLocalityJa: '小郡市小郡255番地1',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct adopted municipal emergency-manual instrument-host statement and separate official facility address',
});
station.note += ' The currently linked 小郡市災害発生時の職員初動マニュアル, download_file/view/44442/735, has a cover dated June 2026. The official plan index 197/702/735, updated 13 July 2026, identifies the adopted June 2026 revisions and links this 38-page manual. Its complete 緊急初動活動チェックリスト page, PDF page 22 in the cached PDF view, explicitly lists the seismic-intensity meter installed in 防災安全課 as an earthquake-information source. The preceding complete response page identifies that division at City Hall; the separate current municipal organization table 1139/1145/4453 independently places 防災安全課 in the main hall. The municipal City Hall guide 1139/1145/2009 names the hall and publishes 福岡県小郡市小郡255-1 in its facility section; the division guide independently publishes 小郡市小郡255番地1. Composite matching uses this explicit instrument reference, the named division and hall, and the sole bundled Ogori reporting station, not coordinates or proximity. The source may use 震度計 for collection/display apparatus in the office; no ground-sensor room, floor, wing or precise within-site position is assigned. The cover and complete relevant PDF text pages were read from the public cached view; direct download returned 403 and screenshot retrieval failed with cache miss, so no visual or physical sensor inspection is claimed. The older May 2025 manual was only a discovery source; the saved placement relies on the currently linked June 2026 edition. No source is claimed to publish the seven-digit station code or a physical installation/transfer date. The English facility label is descriptive; the approved Ogori station name and all earlier source URLs remain unchanged.';
for (const url of [
  'https://www.city.ogori.fukuoka.jp/download_file/view/44442/735',
  'https://www.city.ogori.fukuoka.jp/197/702/735',
  'https://www.city.ogori.fukuoka.jp/1139/1145/4453',
  'https://www.city.ogori.fukuoka.jp/1139/1145/2009',
  'https://www.city.ogori.fukuoka.jp/index.php?cID=4503&r=a001',
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
    assert.ok(row.note.startsWith(old.note));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Ogori City Hall exact address; protected-field and address/precision checks passed.`);
