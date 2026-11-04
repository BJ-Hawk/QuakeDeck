import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  '64FD2931BB95CBF23E4BAC2A874131F225537CCF71AB2533BA720EDD6D1202DE');
const data = JSON.parse(original), before = structuredClone(data);
const row = data.stations.find((r) => r.code === '4021930');
assert.ok(row);
assert.equal(row.nameJa, '大野城市曙町');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.publishedAddressJa && !row.facilityNameJa && !row.facilityNameEn);
assert.ok(!row.note.includes('22774'));

row.note += ' Official contract follow-up checked on 17 October 2026: Onojo City fiscal-2021 procurement index links contract 22774, 震度情報ネットワークシステム移設工事. The complete public contract body states 計測震度計（処理部）移設 一式 and システム試験調整 一式, with work location 大野城市曙町二丁目2番1号, bid date 13 May 2021, contract date 20 May 2021, and performance period 21 May to 21 July 2021. It names 日米電子株式会社 as successful bidder. This is direct evidence for contracted processing-unit relocation work at that published address, not an explicit ground-sensor installation statement, completed-work certificate or source-printed station-code crosswalk. The performance end date is not asserted as the actual sensor-transfer date. The separate current city-site footer gives the same City Hall contact address, but that alone does not identify the reporting ground sensor. Therefore the work-location address is retained in provenance only; facility/address fields and locality precision remain unchanged pending an explicit sensor-host or completed sensor-transfer record. The adopted-plan second-chapter appendix section 2-1 to 2-20, current linked PDF 3_6025_4086_up_b7t5shak.pdf, was fully native-text searched (48 pages, 46050 extracted characters); no instrument-host match was found, but this was not a complete visual inspection of its low-text pages or of the other appendix sections. No placement was inferred from coordinates, locality similarity, the general City Hall address or a processor-only relocation. Approved names, coordinates and prior provenance remain unchanged.';
for (const url of [
  'https://www.city.onojo.fukuoka.jp/kiji0032119/index.html',
  'https://www.city.onojo.fukuoka.jp/kiji0032194/index.html',
  'https://www.city.onojo.fukuoka.jp/bousai/kiji0036025/index.html',
  'https://www.city.onojo.fukuoka.jp/bousai/kiji0036025/3_6025_4086_up_b7t5shak.pdf',
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
console.log(`${apply ? 'Applied' : 'Would apply'} Onojo processing-unit relocation qualification only; placement fields, names, coordinates, unrelated records, order and prior provenance preserved; zero address/precision mismatches. Coverage delta: zero.`);
