import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1430130';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '葉山町堀内');
assert.equal(station.municipalityCode, '1430100');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(station.approvedEnglishName, 'Horiuchi');
assert.equal(data.stations.filter((row) => row.municipalityCode === '1430100').length, 1);
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '葉山町消防本部',
  facilityNameEn: 'Hayama Town Fire Headquarters',
  publishedAddressJa: '神奈川県三浦郡葉山町堀内2050番地の10',
  placementLocalityJa: '三浦郡葉山町堀内2050番地の10',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct municipal fire-annual prefectural intensity-meter host inventory and separate current official facility address',
});
station.note += ' Hayama Town Fire Headquarters 2025 fire annual shoubounennpou7.pdf, complete printed page 72 / PDF page 88, 防災通信機器等 section 2 その他通信機器等, explicitly lists 県計測震度計 at 消防作戦室（防災行政無線室）, one set, connected through the Kanagawa prefectural disaster-administration communications network. The table is dated 1 April 2025. The annual cover and complete installation-table page were read from the official PDF and rendered for visual verification, confirming the row/column association. The independent current municipal 消防情勢・統計 page 1304.html, updated 31 March 2026, names 葉山町消防本部 and explicitly publishes 消防本部・署の所在地 as 神奈川県三浦郡葉山町堀内2050番地の10 in the facility table. Composite matching uses the explicit prefectural meter inventory, named headquarters, matching Horiuchi locality and sole bundled Hayama reporting station; no coordinates, proximity or map position were used. The 2009 annual and 2011 council newsletter were discovery material only; the saved host relies on the 2025 inventory and 2026 facility publication. The listed operations/radio room can describe processing/display equipment and is not asserted as the outdoor ground-sensor position. No source is claimed to print the seven-digit station code, establish a fresh physical inspection or exact installation/transfer date, or locate the ground sensor in a particular room, floor, wing or within-site position. The English facility label is descriptive; the approved Horiuchi station name and Japanese station name remain unchanged.';
for (const url of [
  'https://www.town.hayama.lg.jp/material/files/group/20/shoubounennpou7.pdf',
  'https://www.town.hayama.lg.jp/soshiki/shoubousoumu/1304.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Hayama Town Fire Headquarters exact address; protected-field and address/precision checks passed.`);
