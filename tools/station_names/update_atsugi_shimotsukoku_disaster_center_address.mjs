import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1421248';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '厚木市下津古久');
assert.equal(station.municipalityCode, '1421200');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(station.approvedEnglishName, 'Shimotsukoku');
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '1421200').map((row) => row.code), ['1421220', '1421248']);
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '神奈川県総合防災センター',
  facilityNameEn: 'Kanagawa Prefectural General Disaster Prevention Center',
  publishedAddressJa: '神奈川県厚木市下津古久280',
  placementLocalityJa: '厚木市下津古久280',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct current prefectural seismic-network meter-site diagram and separate official named facility address',
});
station.note += ' The current official Kanagawa earthquake-plan manual index jishin4.html, updated 20 August 2026, links j4-1.pdf. Its complete printed page 743 / PDF page 1, 資料4-1-(1) 神奈川県震度情報ネットワークシステム概要図, explicitly includes 総合防災C in the 県設置震度計（24地点） box. The full diagram was downloaded, text-read and rendered for visual inspection: the meter-site box is separate from the prefectural West Building intensity-processing server and the research-institute connection. This is an explicit named meter site, not an address inferred from a control-room or visitor earthquake simulator. The independent official facility profile tabi-095.html, updated 16 September 2026, names 神奈川県総合防災センター and publishes 神奈川県厚木市下津古久280 in its facility information body. Its link to the center own homepage confirms the facility identity. Composite matching uses the named prefectural meter site, explicit center address and Shimotsukoku station locality. The only other bundled Atsugi station is 1421220 厚木市中町, separately identified at its Nakamachi site; it was not changed. No source is claimed to print the seven-digit code in the diagram, establish a fresh physical inspection or exact sensor installation/transfer date, or identify a particular ground-sensor room, floor, wing or within-site position. The superseded j4-1-1~4-1-10_p389-426.pdf URL returned 404 and was only a discovery lead; saved placement relies on the currently linked j4-1.pdf. The English facility label is descriptive; the approved Shimotsukoku station name, Japanese station name and coordinates remain unchanged.';
for (const url of [
  'https://www.pref.kanagawa.jp/docs/j8g/cnt/f5150/documents/jishin4.html',
  'https://www.pref.kanagawa.jp/documents/85497/j4-1.pdf',
  'https://www.pref.kanagawa.jp/docs/u5r/cnt/f550/tabi-095.html',
  'https://www.pref.kanagawa.jp/docs/zn2/bousaicenter/homepage.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Kanagawa Prefectural General Disaster Prevention Center exact address; protected-field and address/precision checks passed.`);
