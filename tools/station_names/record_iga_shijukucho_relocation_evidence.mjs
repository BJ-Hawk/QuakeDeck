import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2421637';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '伊賀市四十九町');
assert.equal(station.municipalityCode, '2421600');
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
assert.ok(!station.note.includes('20190618_jmaxml_info.pdf'));
station.note += ' JMA notice 20190618_jmaxml_info.pdf, dated 18 June 2019, complete cover and PDF page 3, explicitly pairs former 2421630 伊賀市上野丸之内 with current 2421637 伊賀市四十九町 under 移設観測点. The code-table change took effect at noon on 18 July 2019; this is not asserted as the exact physical sensor-transfer date. The cover and complete relevant table page were text-read and rendered for visual inspection. The official municipal October 2018 procurement index 6028.html links contract 2018001148kekka.pdf, whose complete two pages were text-read: the project title is 伊賀市地すべり・山崩れ観測設備および震度情報ネットワークシステム移設工事, location 伊賀市四十九町外地内, awarded to JRC System Service Chubu, with the originally stated work period 24 October 2018 to 31 January 2019. The fiscal 2019 periodic audit 1teikikansa.pdf, complete printed page 9 / PDF page 11, places that named relocation project under carried-over hall-relocation expenses and records budget and expenditure both 9,656,280 yen, 100 percent executed as of 30 September 2019; the entire page was text-read and visually inspected to confirm the row association. Full expenditure is not independently asserted to certify the exact ground-sensor destination or transfer date. The procurement result provides no individual sensor parcel, and a network-system relocation can include processing/display equipment. The currently linked February 2026 earthquake plan (2602_tiiki_shinsai.pdf, 256 pages) and April 2026 first appendix (2602_tiiki_si1.pdf, 229 pages) were fully normalized-text searched for instrument terms and yielded only generic gas/instrument guidance, not a named host; this is not complete visual inspection of every page. These sources establish relocation provenance and guide further destination research, not a verified current host/address. Facility and exact-address fields remain blank; approved English/Japanese names, coordinates and locality precision remain unchanged. Do not assign the former Ueno Marunouchi parcel or current City Hall address without explicit destination evidence.';
for (const url of [
  'https://xml.kishou.go.jp/20190618_jmaxml_info.pdf',
  'https://www.city.iga.lg.jp/0000006028.html',
  'https://www.city.iga.lg.jp/cmsfiles/contents/0000006/6028/2018001148kekka.pdf',
  'https://www.city.iga.lg.jp/cmsfiles/contents/0000001/1299/1teikikansa.pdf',
  'https://www.city.iga.lg.jp/0000003257.html',
  'https://www.city.iga.lg.jp/cmsfiles/contents/0000003/3257/2602_tiiki_shinsai.pdf',
  'https://www.city.iga.lg.jp/cmsfiles/contents/0000003/3257/2602_tiiki_si1.pdf',
]) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);

assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((row) => row.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  if (row.code !== code) assert.deepEqual(row, old);
  else {
    for (const key of Object.keys(old)) if (!['note', 'sourceUrls'].includes(key)) assert.deepEqual(row[key], old[key]);
    assert.deepEqual(Object.keys(row), Object.keys(old));
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
    assert.ok(row.note.startsWith(old.note));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} relocation provenance only; all placement/name/coordinate fields and other rows unchanged.`);
