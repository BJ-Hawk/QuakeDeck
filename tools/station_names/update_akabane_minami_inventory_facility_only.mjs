import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  'DC36CD02028E6E3903C1CCBA6B5F99F5CD18128E91606C26157AEDE738FEE20D');
const data = JSON.parse(original), before = structuredClone(data);
const row = data.stations.find((r) => r.code === '1311753');
assert.ok(row);
assert.equal(row.nameJa, '東京北区赤羽南');
assert.equal(row.approvedEnglishName, 'Akabaneminami');
assert.equal(row.providerJa, '地方公共団体');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);

row.facilityNameJa = '赤羽消防署';
row.facilityNameEn = 'Akabane Fire Station';
row.metadataStatus = 'Dated public installation-host identity; current ground-sensor host-site address continuity remains unverified';
row.note += ' Facility-identity evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Tokyo Fire Department inventory, heading last updated 11 January 2022, explicitly names 赤羽消防署 in the installation-facility column for TFD 0139170, with the equipment note 2018年3月1日から、高見沢サイバネティックス. Its separate TFD 015 row names the same facility with 2018年2月まで、明星電気. Both complete selected rows and column headings were read publicly without login or restricted waveform access. This supports the named inventory-host identity, not a parcel inferred from the rows coordinates or proof that every row was newly inspected on the page heading date. Kita Ward official newsletter 北区ニュース, 20 February 2020, complete printed/PDF page 2, was text-read, rendered and visually inspected with its date and complete 赤羽消防署新庁舎が完成します notice. It announces new-office operation from 10 March 2020 at 赤羽南1-10-4, while normal operations continue until then at the temporary office 神谷3-11-22. The current Tokyo Metropolitan Government police/fire directory independently publishes 赤羽消防署 at 北区赤羽南1-10-4, telephone 03-3902-0119; its full named row was read. Neither the office-opening announcement nor the current directory says where the ground sensor remained or was transferred. The selected inventory rows equipment dates precede that 2020 office return, so the new-office address is retained as context only, not assigned to the reporting instrument. Facility identity is recorded at Akabane Fire Station level only; publishedAddressJa stays blank and placementLocalityJa/placementPrecision remain unchanged pending an explicit sensor-site or completed instrument-transfer source. The previously discovered November 2019 third-party relocation-tender lead is not treated as an inspected official specification or a completion record. No source-printed seven-digit JMA-code crosswalk, physical visit, exact sensor-transfer date, room/floor or surveyed enclosure is asserted. No coordinates, marker proximity, general office-move assumption or generic installation policy was used. The English facility name is descriptive; approved station names, coordinates and previous provenance are preserved.';
for (const url of [
  'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html',
  'https://www.city.kita.lg.jp/_res/projects/default_project/_page_/001/013/921/020220.pdf',
  'https://www.metro.tokyo.lg.jp/tosei/iken-sodan/sodan/kesatsu',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'metadataStatus', 'sourceUrls', 'note']);
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
    for (const key of Object.keys(old)) if (!allowed.has(key)) assert.deepEqual(r[key], old[key]);
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
console.log(`${apply ? 'Applied' : 'Would apply'} Akabane Fire Station facility identity only; address and precision unchanged; protected fields, unrelated rows, order and prior provenance preserved; zero address/precision mismatches. Coverage delta only: facility-only +1, remaining without facility/address -1; exact and municipality/ward totals unchanged.`);
