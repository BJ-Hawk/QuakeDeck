import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2421437';
const row = data.stations.find((station) => station.code === code);
assert.ok(row);
assert.equal(row.nameJa, 'いなべ市藤原町市場');
assert.equal(row.approvedEnglishName, 'Fujiwaracho Ichiba');
assert.equal(row.providerJa, '地方公共団体');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
assert.ok(!row.note.includes('r6zimuzigyouhyouka02.pdf'));

row.note += ' The official fiscal 2024 municipal project-evaluation report r6zimuzigyouhyouka02.pdf (362 PDF pages) supplies direct instrument-line host evidence, but does not resolve the individual Fujiwara building. Complete printed page 30 / PDF page 44, form dated 25 July 2025, item 5 地震計（震度計）回線使用料, lists 本庁舎, 大安北分団詰所, 員弁第１分団詰所 and 藤原第１分団詰所, with a combined line charge of 79,200 yen. Complete printed page 38 / PDF page 52, also dated 25 July 2025, explicitly lists two 第１分団 facilities within the five detailed Fujiwara brigade sites: 藤原町市場125-4 and 藤原町坂本56-3. Both complete pages were downloaded, normalized-text read, rendered and visually inspected. The other three listed Fujiwara brigade facilities are 古田1612-18, 長尾333-1 and 下野尻2090. The summary on the same page says three Fujiwara facilities while the detailed inventory lists five; that summary is not evidence that either First Division parcel had closed. The explicit line-host name therefore cannot by itself select one of the two published First Division addresses. The station locality 市場 is not used to disambiguate by proximity or locality-name similarity. Facility and exact-address fields remain blank and locality precision is unchanged. Further research must establish the actual meter-bearing building and any completed transfer associated with brigade-facility consolidation before assigning a parcel. No physical inspection, room, floor, sensor position or installation/transfer date is asserted. Approved English/Japanese station names, coordinates and existing provenance are unchanged.';
const url = 'https://www.city.inabe.mie.jp/_res/projects/default_project/_page_/001/014/543/r6zimuzigyouhyouka02.pdf';
if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((station) => station.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const station = data.stations[i], old = before.stations[i];
  assert.equal(station.code, old.code);
  if (station.code !== code) assert.deepEqual(station, old);
  else {
    assert.deepEqual(Object.keys(station), Object.keys(old));
    for (const key of Object.keys(old)) if (!['note', 'sourceUrls'].includes(key)) assert.deepEqual(station[key], old[key]);
    for (const previousUrl of old.sourceUrls) assert.ok(station.sourceUrls.includes(previousUrl));
    assert.ok(station.note.startsWith(old.note));
  }
  assert.equal(station.placementPrecision === 'exact_address', Boolean(station.publishedAddressJa), `Address/precision mismatch ${station.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} instrument-host ambiguity evidence only; names, coordinates, placement fields and other records unchanged; zero address/precision mismatches.`);
