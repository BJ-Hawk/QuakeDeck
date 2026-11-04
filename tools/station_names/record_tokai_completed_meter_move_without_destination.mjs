import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '0834132';
const row = data.stations.find((r) => r.code === code);
assert.ok(row);
assert.equal(row.nameJa, '東海村東海');
assert.equal(row.approvedEnglishName, 'Tokai');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
const marker = 'Tokai completed meter-relocation evidence checked on 17 October 2026:';
assert.ok(!row.note.includes(marker), 'Already recorded');
row.note += ` ${marker} the official village document 平成23年度 村政懇談会質問およびその後の対応 was inspected at complete PDF page 10, including the column headings and the full row No.25 (白方, meeting date 15 July 2011, 原子力対策課). The page was text-read, rendered and visually inspected. The December 2011 progress column plans meter relocation by late March 2012; the distinct May 2012 progress column states that, after consultation with Ibaraki Prefecture about the installation site, meter-relocation work was completed on 27 March 2012. This is explicit completed-work evidence, not merely a proposed transfer. However, that row does not name either the origin or destination facility, street address or instrument enclosure, and the question's 白方 district identifies the consultation venue/community, not the meter site. The current 東海村東海 reporting locality is preserved; neither the village-office general address nor a nuclear-facility or neighboring municipality instrument is substituted. Facility/address fields remain blank pending a direct destination/current-host record. This source does not publish a seven-digit JMA-code crosswalk or prove there were no later moves. No host is inferred from coordinates, a shared locality, proximity, a generic installation rule or unrelated rows on the same page. Approved names, coordinates and prior provenance remain unchanged.`;
const url = 'https://www.vill.tokai.ibaraki.jp/material/files/group/7/103001_20120710_0001.pdf';
if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

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
    for (const source of old.sourceUrls) assert.ok(r.sourceUrls.includes(source));
  }
  assert.equal(r.placementPrecision === 'exact_address', Boolean(r.publishedAddressJa), `Address/precision mismatch ${r.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} Tokai completed-relocation provenance without an unsupported destination; all placement fields, names, coordinates, unrelated rows, order and prior provenance preserved; zero address/precision mismatches. No coverage-count change.`);
