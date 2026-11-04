import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html';
const addressSource = 'https://www.metro.tokyo.lg.jp/tosei/iken-sodan/sodan/kesatsu';
const updates = [
  ['1310353', '東京港区白金', 'Shirokane', 'TFD.001', '高輪消防署', 'Takanawa Fire Station', '東京都港区白金2-4-12'],
  ['1311153', '東京大田区多摩川', 'Tamagawa', 'TFD.002', '矢口消防署', 'Yaguchi Fire Station', '東京都大田区多摩川2-5-20'],
  ['1311253', '東京世田谷区成城', 'Seijo', 'TFD.003', '成城消防署', 'Seijo Fire Station', '東京都世田谷区成城1-21-14'],
  ['1311452', '東京中野区中央', 'Chuo', 'TFD.004', '中野消防署', 'Nakano Fire Station', '東京都中野区中央3-25-3'],
  ['1310550', '東京文京区本郷', 'Hongo', 'TFD.024', '本郷消防署', 'Hongo Fire Station', '東京都文京区本郷7-1-11'],
  ['1310352', '東京港区南青山', 'Minamiaoyama', 'TFD.032', '赤坂消防署', 'Akasaka Fire Station', '東京都港区南青山2-16-9'],
  ['1311252', '東京世田谷区中町', 'Nakamachi', 'TFD.033', '玉川消防署', 'Tamagawa Fire Station', '東京都世田谷区中町3-1-19'],
  ['1311150', '東京大田区大森東', 'Omorihigashi', 'TFD.0139040', '大森消防署', 'Omori Fire Station', '東京都大田区大森東1-32-8'],
  ['1311250', '東京世田谷区三軒茶屋', 'Sangenjaya', 'TFD.0139050', '世田谷消防署', 'Setagaya Fire Station', '東京都世田谷区三軒茶屋2-33-21'],
  ['1310451', '東京新宿区百人町', 'Hyakunincho', 'TFD.0139160', '新宿消防署', 'Shinjuku Fire Station', '東京都新宿区百人町3-29-4'],
];

for (const [code, nameJa, english, inventoryId, host, facilityNameEn, address] of updates) {
  const row = data.stations.find((station) => station.code === code);
  assert.ok(row, `Missing ${code}`);
  assert.equal(row.nameJa, nameJa);
  assert.equal(row.approvedEnglishName, english);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa, `Existing placement ${code}`);
  Object.assign(row, {
    facilityNameJa: `東京消防庁${host}`,
    facilityNameEn,
    publishedAddressJa: address,
    placementLocalityJa: address,
    placementPrecision: 'exact_address',
    metadataStatus: 'Public university fire-service installation inventory (2022) and official metropolitan facility address',
  });
  let qualification = 'The selected row identifies a Meisei instrument.';
  if (inventoryId === 'TFD.0139040') qualification = 'The row identifies a Takamisawa instrument and records the pre-21-February-2012 identifier 0400200; identifier history is not treated as a relocation date.';
  if (inventoryId === 'TFD.0139050') qualification = 'The row identifies a Takamisawa instrument and records the pre-21-February-2012 identifier 0400300; identifier history is not treated as a relocation date.';
  if (inventoryId === 'TFD.0139160') qualification = 'The selected Takamisawa row applies from 1 March 2018; the separate TFD.014 Meisei row is explicitly marked through February 2018 and is not substituted for the selected row.';
  row.note += ` Placement evidence checked on 17 October 2026: the public SK-net Tokyo Fire Department observation inventory, maintained by the University of Tokyo Earthquake Research Institute, explicitly names ${host} in the installation-facility column for ${inventoryId}. Its heading states last updated 11 January 2022. The complete selected row and column headings were read, without logging in or obtaining restricted waveform data. ${qualification} Tokyo metropolitan government's current police/fire contact table independently lists the named fire station and its address, recorded here as ${address}. The meter host comes from the installation inventory, not a general rule about fire stations, the contact-table row alone, a jurisdiction list or proximity. Association with this reporting record uses the explicit host identity and independently published matching locality, not a source-printed seven-digit JMA-code crosswalk. The dated inventory is retained as a source-age qualification, not a fresh 2026 instrument inspection or full operational-continuity recertification. No room, floor, exact within-site sensor position, new installation date or physical visit is asserted. Facility English is descriptive, not claimed as an official English title; approved Japanese and English station names and coordinates remain unchanged.`;
  for (const url of [inventory, addressSource]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}

const codes = new Set(updates.map(([code]) => code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((station) => station.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  if (!codes.has(row.code)) assert.deepEqual(row, old);
  else {
    assert.deepEqual(Object.keys(row), Object.keys(old));
    for (const key of Object.keys(old)) if (!allowed.has(key)) assert.deepEqual(row[key], old[key]);
    assert.ok(row.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} ten dated-inventory Tokyo fire-host addresses; all protected fields, other records, row order and existing provenance preserved; zero address/precision mismatches. Coverage delta only: exact +10, municipality/ward -10, remaining facility/address research -10; facility-only unchanged.`);
