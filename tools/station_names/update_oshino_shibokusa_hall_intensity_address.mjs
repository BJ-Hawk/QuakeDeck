import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1942432';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '忍野村忍草');
assert.equal(station.municipalityCode, '1942400');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(data.stations.filter((row) => row.municipalityCode === '1942400').length, 1);
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '忍野村役場',
  facilityNameEn: 'Oshino Village Hall',
  publishedAddressJa: '山梨県南都留郡忍野村忍草1514',
  placementLocalityJa: '南都留郡忍野村忍草1514',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct currently linked municipal disaster-plan instrument-host statement and separate official facility address',
});
station.note += ' The official municipal disaster-plan index 1772.html, updated 16 June 2025, identifies the adopted 2025 revision and links earthquake volume 2179.pdf. Its printed page 207 / PDF page 33, section 4 地震情報の収集, explicitly states that the village checks intensity from the instrumental seismic-intensity meter installed at the village office building. The complete relevant page, including its section heading and preceding transmission diagram, was read in the publicly accessible cached PDF text, not merely a search preview. The separately linked cover/contents 2547.pdf identifies May 2025 and the Oshino Village Disaster Management Council. The official hall access guide 1669.html, updated 27 November 2023, independently names 忍野村役場 and publishes 山梨県南都留郡忍野村忍草1514 in the facility section, not only a departmental contact footer. Composite matching uses the explicit meter host and the sole bundled Oshino reporting station, whose 忍草 locality matches the published hall address; no coordinates or proximity were used. Direct PDF retrieval returned 404 and screenshot retrieval failed with cache miss during this check, so the PDF evidence was text-read but not visually verified; no visual inspection or fresh physical sensor inspection is claimed. The currently linked earthquake volume has an older numbered attachment URL and is not claimed independently dated May 2025 on its own cover. No source is claimed to print the seven-digit station code, a sensor installation/transfer date, a particular building wing, room, floor or within-site position. The English facility label is descriptive; the approved Shibokusa station name remains unchanged.';
for (const url of [
  'https://www.vill.oshino.lg.jp/uploaded/attachment/2179.pdf',
  'https://www.vill.oshino.lg.jp/uploaded/attachment/2547.pdf',
  'https://www.vill.oshino.lg.jp/page/1772.html',
  'https://www.vill.oshino.lg.jp/page/1669.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Oshino Village Hall exact address; protected-field and address/precision checks passed.`);
