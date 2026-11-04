import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1123431';
const row = data.stations.find((r) => r.code === code);
assert.ok(row);
assert.equal(row.nameJa, '八潮市中央');
assert.equal(row.approvedEnglishName, 'Chuo');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
const marker = 'Yashio new-hall meter-transfer evidence checked on 17 October 2026:';
assert.ok(!row.note.includes(marker), 'Already recorded');
row.note += ` ${marker} the public University of Tokyo Earthquake Research Institute SK-net Saitama inventory, heading last updated 16 May 2026, explicitly names 八潮市役所 as the installation facility for SIT.034; its complete selected row and column headings were read. Yashio City newsletter No.877 dated 10 December 2023, complete printed/PDF page 2, was text-read, rendered and visually inspected. Its 防災設備の移設 notice explicitly describes disaster-equipment relocation to the new hall and a planned interruption of JMA-published local intensity observations for meter-transfer work from 27 December 2023 at 09:00 to 10 January 2024 at noon. The notice expressly labels that interval planned; it does not certify completed meter transfer or its actual destination parcel/position. The official new-hall completion guide, updated 20 August 2024, separately records building completion on 31 October 2023 and office opening on 4 January 2024, neither of which is an instrument-transfer completion date. The current official City Hall guide independently publishes 埼玉県八潮市中央一丁目2番地1, but that general facility address is not by itself proof that the reporting instrument moved there. The inventory page's 2026 date is not treated as an individual post-transfer physical inspection or a statement distinguishing the former instrument position from the new one. Facility/address fields remain blank pending a completed-transfer record or explicit current meter-host/site statement. No coordinates, proximity, proposed floor-plan position, physical visit or source-printed seven-digit JMA-code crosswalk is used. Approved names, coordinates and earlier provenance remain unchanged.`;
for (const url of [
  'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html',
  'https://www.city.yashio.lg.jp/shisei/koho_kocho/kohoyashio/r5/kohoyashio0512.files/yashio_12P1-P6.pdf',
  'https://www.city.yashio.lg.jp/shisei/assetmanagement/tyousyataishinka/shinchosya-kouji/shinchoshakansei.html',
  'https://www.city.yashio.lg.jp/kurashi/shisetsuguide/shisetsuannai/shiyakusyo/index.html',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((r) => r.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const r = data.stations[i], old = before.stations[i];
  assert.equal(r.code, old.code);
  if (r.code !== code) assert.deepEqual(r, old);
  else {
    assert.deepEqual(Object.keys(r), Object.keys(old));
    for (const key of Object.keys(old)) if (!['note', 'sourceUrls'].includes(key)) assert.deepEqual(r[key], old[key]);
    assert.ok(r.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(r.sourceUrls.includes(url));
  }
  assert.equal(r.placementPrecision === 'exact_address', Boolean(r.publishedAddressJa), `Address/precision mismatch ${r.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} Yashio planned meter-transfer provenance only; all placement fields, names, coordinates, unrelated rows and previous evidence preserved; zero address/precision mismatches. No coverage-count change.`);
