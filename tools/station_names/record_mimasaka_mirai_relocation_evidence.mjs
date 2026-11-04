import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '3321538';
const row = data.stations.find((station) => station.code === code);
assert.ok(row);
assert.equal(row.nameJa, '美作市美来');
assert.equal(row.approvedEnglishName, 'Mirai');
assert.equal(row.providerJa, '地方公共団体');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
assert.ok(!row.note.includes('kihonnsekkeigaiyou.pdf'));

row.note += ' Official relocation evidence checked on 17 October 2026: the 24 June 2025 JMA XML code-change notice, complete PDF page 4, explicitly pairs former 3321533 美作市栄町 with current 3321538 美作市美来 under relocated observations. PDF page 2 sets the code-table update for 24 July 2025 at 12:00; that is not the physical relocation or observation-start date. JMA Okayama December 2025 report, complete printed page 5 / PDF page 6, Table 3 footnotes 3 and 4, records Sakaemachi observations ending on 4 May 2025 at 09:00 and successor Mirai observations beginning on 17 June 2025 at 16:15. The complete code-pairing table and report page were text-read, rendered and visually inspected. The city public new-hall basic-design summary kihonnsekkeigaiyou.pdf, nine PDF pages, is dated September 2022 on its visually inspected cover. Complete drawing A-03, PDF page 5, explicitly labels 地震計 on the planned City Hall grounds; the complete drawing was rendered and visually inspected because most extracted Japanese text is misencoded. This is a direct planned-host lead, not proof that the reporting meter was installed there as drawn. The official publication page describes the document as a basic design preceding detailed implementation design. No as-built drawing, completed sensor-transfer record or current individual host inventory has yet tied this planned instrument to station 3321538. Facility/address fields therefore remain blank, locality precision is unchanged, and neither the old Sakaemachi parcel nor the new hall general contact address is assigned. Seek public completed-placement evidence; do not infer the host from locality similarity, catalogue coordinates or the design alone. No physical inspection or precise installed sensor position is asserted. Approved station names, coordinates and previous provenance are preserved.';
for (const url of [
  'https://xml.kishou.go.jp/20250624_jmaxml_info.pdf',
  'https://www.data.jma.go.jp/okayama/jishin/geppou/202512.pdf',
  'https://www.city.mimasaka.lg.jp/shisei/shiyakusho/new_chosha/shinchoshakensetsu/3717.html',
  'https://www.city.mimasaka.lg.jp/material/files/group/6/kihonnsekkeigaiyou.pdf',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} relocation and planned-host evidence only; placement fields, names, coordinates and all other records unchanged; zero address/precision mismatches.`);
