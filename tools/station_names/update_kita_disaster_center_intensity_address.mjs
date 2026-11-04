import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1311730';
const s = data.stations.find((row) => row.code === code);
assert.ok(s, `Missing station ${code}`);
assert.equal(s.nameJa, '東京北区西ヶ原');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa, `Existing placement ${code}`);
Object.assign(s, {
  facilityNameJa: '東京都北区防災センター（地震の科学館）',
  facilityNameEn: 'Kita City Disaster Prevention Center (Earthquake Science Museum)',
  publishedAddressJa: '東京都北区西ケ原2-1-6',
  placementLocalityJa: '北区西ケ原2-1-6',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct official ward instrument-host inventory and published facility address',
  note: 'Kita Ward national-resilience regional plan fiscal 2024 individual-project list, printed/PDF page 2, row 46, explicitly lists 計測震度計の更新 at 防災センター. The complete page was text-read and visually inspected, with the location column distinguished from the project period and expenditure columns. This establishes the named instrument host, not proof of a completed renewal date from its fiscal 2023 period or zero fiscal 2024 budget. The official earthquake-response plan appendix, shinsaishiryo.pdf, printed 資-26 / PDF page 28, section 26 北区防災無線システムの全体像, was also visually inspected: it connects the intensity meter to Disaster Prevention Center and separately identifies an intensity-display device at Ward Hall. This is an operational network diagram, not a parcel or within-site placement survey. The ward facility guide, updated 25 August 2026, directly identifies 東京都北区防災センター（地震の科学館） and publishes 東京都北区西ケ原2-1-6. The meter is not inferred from the museum earthquake simulator or catalogue coordinates. The source host and published locality jointly identify the Nishigahara reporting station; no source is claimed to publish its seven-digit JMA code. No current room, floor, precise outdoor position, installation date, or fresh meter inspection is asserted. The English facility label is descriptive, not claimed as an official English title; approved English and Japanese station names remain unchanged.',
});
for (const url of [
  'https://www.city.kita.lg.jp/_res/projects/default_project/_page_/001/002/662/kokudokyojinnka-r6kobetujigyou.pdf',
  'https://www.city.kita.lg.jp/_res/projects/default_project/_page_/001/002/648/shinsaishiryo.pdf',
  'https://www.city.kita.lg.jp/safety/disaster/1002639/1002675.html',
]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);

const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((row) => row.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const current = data.stations[i];
  const old = before.stations[i];
  assert.equal(current.code, old.code);
  if (current.code !== code) assert.deepEqual(current, old);
  else {
    for (const key of new Set([...Object.keys(current), ...Object.keys(old)])) if (!allowed.has(key)) assert.deepEqual(current[key], old[key]);
    for (const url of old.sourceUrls) assert.ok(current.sourceUrls.includes(url));
  }
  assert.equal(current.placementPrecision === 'exact_address', Boolean(current.publishedAddressJa), `Address/precision mismatch ${current.code}`);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Kita Disaster Prevention Center host-address update; protected-field and address/precision checks passed.`);
