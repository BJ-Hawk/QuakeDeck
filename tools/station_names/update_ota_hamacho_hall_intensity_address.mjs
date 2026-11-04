import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1020530';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '太田市浜町');
assert.equal(station.municipalityCode, '1020500');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(data.stations.filter((row) => row.municipalityCode === '1020500' && row.nameJa.includes('浜町')).length, 1);
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '太田市役所',
  facilityNameEn: 'Ota City Hall',
  publishedAddressJa: '群馬県太田市浜町2番35号',
  placementLocalityJa: '太田市浜町2番35号',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct adopted municipal disaster-plan instrument-host statement and independently corroborated official hall address',
});
station.note += ' The adopted April 2026 Ota disaster plan, 39566.pdf, printed page 応【震災】－1－2 / PDF page 279, table 災害時の情報内容と収集手段, explicitly lists 市役所震度計 as an earthquake-information source and 危機管理室内計測震度計 as its collection means. The entire 448-page plan was text-searched; the cover and complete relevant page were text-read and visually inspected. The live municipal plan index 1690.html identifies the April 2026 adopted revision, not a draft. The official City Hall access guide 1014037.html identifies the facility and publishes 群馬県太田市浜町2番35号; Gunma Prefecture election administration facility table 14636.html independently pairs 太田市役所2階ラウンジ with 太田市浜町2番35号. The latter source corroborates the facility address only, not an instrument location. Composite matching uses the explicit City Hall meter reference and unique 浜町 reporting locality among bundled Ota stations, not coordinates or proximity. The table does not distinguish the ground sensor from collection/display apparatus inside the crisis-management office; no office, room, floor, building wing or exact within-site sensor position is assigned. The other Ota reporting localities (粕川町, 新田金井町, 大原町 and the NIED 西本町 site) are not assigned from this main-hall statement. No source is claimed to publish the seven-digit code, sensor installation/transfer date, or a fresh physical inspection. The English facility label is descriptive; approved station names remain unchanged.';
for (const url of [
  'https://www.city.ota.gunma.jp/uploaded/attachment/39566.pdf',
  'https://www.city.ota.gunma.jp/page/1690.html',
  'https://www.city.ota.gunma.jp/page/1014037.html',
  'https://www.pref.gunma.jp/page/14636.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Ota City Hall exact address; protected-field and address/precision checks passed.`);
