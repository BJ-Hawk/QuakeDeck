import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1942232';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '道志村釜之前');
assert.equal(station.municipalityCode, '1942200');
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
const addition = ' JMA notice dated 17 February 2021, 20210217_jmaxml_info_1.pdf, cover and complete PDF page 3, explicitly pairs old 1942231 道志村役場 with new 1942232 道志村釜之前 under section 2 移設観測点. The cover says the code-table change takes effect at 12:00 on 17 March 2021; this is a parameter effective time, not an independently proven physical transfer date. The complete relevant table page and cover were extracted and read from the official PDF and rendered for visual review. The table supplies codes and names but no destination facility or parcel address. A search-index preview of the village fiscal-2012 disaster plan mentions a meter at Village Hall; direct retrieval timed out and the full older plan was not inspected. That earlier hall statement predates this documented relocation and is not carried forward as the current host. Obtain a current installation inventory or explicit destination/continuity evidence before assigning any facility or address. This provenance-only update leaves the existing locality precision and approved station name unchanged.';
assert.ok(!station.note.includes('20210217_jmaxml_info_1.pdf'));
station.note += addition;
const url = 'https://xml.kishou.go.jp/20210217_jmaxml_info_1.pdf';
if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);

const allowed = new Set(['note', 'sourceUrls']);
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
    for (const oldUrl of old.sourceUrls) assert.ok(row.sourceUrls.includes(oldUrl));
    assert.ok(row.note.startsWith(old.note));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} relocation provenance only; all placement and protected fields unchanged, address/precision checks passed.`);
