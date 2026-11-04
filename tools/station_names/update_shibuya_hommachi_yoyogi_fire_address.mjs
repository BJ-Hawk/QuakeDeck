import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  '9D51D999F07EF7357ACF3900100110E4F1B104C09AC51EC44ECC84EDDF479FE4',
  'Source changed since the inspected checkpoint; inspect before applying');
const data = JSON.parse(original);
const before = structuredClone(data);
const code = '1311350';
const row = data.stations.find((r) => r.code === code);
assert.ok(row);
assert.equal(row.nameJa, '東京渋谷区本町');
assert.equal(row.approvedEnglishName, 'Hommachi');
assert.equal(row.providerJa, '地方公共団体');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.publishedAddressJa && !row.facilityNameJa && !row.facilityNameEn);

const address = '東京都渋谷区本町1-6-6';
Object.assign(row, {
  facilityNameJa: '渋谷消防署代々木出張所',
  facilityNameEn: 'Shibuya Fire Station, Yoyogi Branch',
  publishedAddressJa: address,
  placementLocalityJa: address,
  placementPrecision: 'exact_address',
  metadataStatus: 'Public university installation-host inventory and independent official fire-service address guides',
});
row.note += ' Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Tokyo Fire Department inventory, heading last updated 11 January 2022, explicitly names 渋谷消防署代々木出張所 as the installation facility for TFD 0139130, with the note that its observation-point identifier before 21 February 2012 was 0530640. The separate TFD 013 row also names this same branch; the complete selected rows and column headings were read publicly without registration or waveform access. The identifier note is not interpreted as an instrument-move date. Two independent official Tokyo Fire Department / Shibuya Fire Station guides, 建物の使用を開始する方へ and 届出内容に関する注意事項, each complete one-page PDF, were text-read, rendered and visually inspected with their headings and full address tables. Both publish the named Yoyogi Branch at 渋谷区本町1-6-6; the postal guide also gives postcode 151-0071. This identifies the inventory host in the official observation locality 本町, not the separate Shibuya City Hall site in 宇田川町 or the ward administrative 本町出張所. The stored address is the named host-facility address, not a surveyed sensor enclosure, floor or within-parcel position. The dated university inventory is direct published host evidence, not a fresh 2026 instrument inspection or full operational-continuity recertification. No source-printed seven-digit JMA-code crosswalk, physical visit or sensor-transfer date is asserted. Association uses the explicitly named installation facility and its independently published locality/address, not coordinates, proximity or a generic fire-station policy. The facility English is descriptive; approved station names, coordinates and prior provenance remain unchanged.';
for (const url of [
  'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html',
  'https://www.tfd.metro.tokyo.lg.jp/content/000044114.pdf',
  'https://www.tfd.metro.tokyo.lg.jp/content/000041970.pdf',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa',
  'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
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
console.log(`${apply ? 'Applied' : 'Would apply'} Shibuya Hommachi / Yoyogi fire-branch host address; protected fields, unrelated rows, order and prior provenance preserved; zero address/precision mismatches. Coverage delta only: exact +1, municipality/ward -1, remaining facility/address research -1; facility-only unchanged.`);
