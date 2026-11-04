import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  '3C81D688ACDBC30D91A117BD4273002BDA27FEA8AE5DAA4E78D5636D37D6ED86');
const data = JSON.parse(original), before = structuredClone(data);
const row = data.stations.find((r) => r.code === '2120932');
assert.ok(row);
assert.equal(row.nameJa, '羽島市竹鼻町');
assert.equal(row.approvedEnglishName, 'Takehanacho');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.publishedAddressJa && !row.facilityNameJa && !row.facilityNameEn);

row.note += ' Public relocation evidence checked on 17 October 2026: the official Hashima City crisis-management index links the notice dated 27 March 2024, 震度計を移設. Its complete observation section was read: the meter described as installed at Hashima City Hall was unavailable from 7 March for relocation and was restored on 27 March. The notice does not explicitly identify the completed destination or say that the sensor remained on the same parcel. Its general City Hall footer at 竹鼻町55 is not a destination statement. The February 2024 municipal newsletter, actual seven-page PDF, was fully searched as whitespace-normalized native text (17588 extracted characters); complete PDF page 5, printed pages 8-9, was text-read, rendered and visually inspected. Its separate boxed meter notice, printed page 9, announces relocation and no published local intensity through late March but supplies no destination. The adjacent elderly-welfare-center closure item is unrelated and must not be treated as the meter host. The FY2023 administrative report, 192 pages and 197451 extracted characters, was fully searched as whitespace-normalized native text for intensity/seismometer terms. Its only matching page, complete PDF/printed page 24, was text-read, rendered and visually inspected: the disaster-facility-management expenditure table records 3630000 yen for 震度情報ネットワークシステム移設工事, but gives no individual host, work-site address or seven-digit station association. This expenditure is retained as a precise contract/site-specification discovery lead, not proof of sensor destination. These are native-text searches and selected-page visual inspections, not whole-volume visual/OCR negatives. Following the normal city procurement index and results page reaches the public i-ppi construction-results service; the ordinary public search submission returned a generic Error, including through its normal entry/session flow. This is a failed query, not evidence that no contract record exists, and no registration, login, access bypass or contact was attempted. Seek the completed relocation work-site specification, as-built record or later individual installation inventory before assigning a current facility/address. No placement is inferred from rounded coordinates, a marker, locality similarity, the office footer, an undated installation case or the relocation expenditure alone. Facility/address fields and locality precision remain unchanged; approved names, coordinates and prior provenance are preserved.';
for (const url of [
  'https://www.city.hashima.lg.jp/1088.html',
  'https://www.city.hashima.lg.jp/5434.html',
  'https://www.city.hashima.lg.jp/secure/4992/2402.pdf',
  'https://www.city.hashima.lg.jp/secure/2668/R5gyoseihoukokusyo.pdf',
  'https://www.city.hashima.lg.jp/3603.html',
  'https://www.city.hashima.lg.jp/1429.html',
  'https://www.i-ppi.jp/IPPI/SearchServices/Web/Index.htm',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((r) => r.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const r = data.stations[i], old = before.stations[i];
  assert.equal(r.code, old.code);
  if (r.code !== row.code) assert.deepEqual(r, old);
  else {
    assert.deepEqual(Object.keys(r), Object.keys(old));
    for (const key of Object.keys(old)) if (!['note', 'sourceUrls'].includes(key)) assert.deepEqual(r[key], old[key]);
    assert.ok(r.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(r.sourceUrls.includes(url));
  }
  assert.equal(r.placementPrecision === 'exact_address', Boolean(r.publishedAddressJa),
    `Address/precision mismatch ${r.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} Hashima completed-move destination-gap provenance only; placements, names, coordinates, unrelated records, order and previous provenance preserved; zero address/precision mismatches. Coverage delta: zero.`);
