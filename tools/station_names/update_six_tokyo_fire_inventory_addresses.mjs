import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html';
const addressSource = 'https://www.metro.tokyo.lg.jp/tosei/iken-sodan/sodan/kesatsu';
const updates = [
  ['1310151', '東京千代田区麹町', 'Kojimachi', 'TFD.0139030', '麹町消防署', 'Kojimachi Fire Station', '東京都千代田区麹町1-12', 'The row records the former pre-21-February-2012 identifier 0400100; that is an identifier history, not proof of an instrument move.'],
  ['1310852', '東京江東区亀戸', 'Kameido', 'TFD.026', '城東消防署', 'Joto Fire Station', '東京都江東区亀戸6-42-9', 'The row identifies a Meisei instrument and the Joto station itself, not a generic installation policy for all fire facilities.'],
  ['1310951', '東京品川区平塚', 'Hiratsuka', 'TFD.021', '荏原消防署', 'Ebara Fire Station', '東京都品川区平塚3-16-20', 'The row identifies a Meisei instrument at Ebara Fire Station; the Hiratsuka locality is established by the independently published host address.'],
  ['1310250', '東京中央区日本橋兜町', 'Nihombashikabutocho', 'TFD.0139140', '日本橋消防署', 'Nihonbashi Fire Station', '東京都中央区日本橋兜町14-12', 'The selected row applies from 1 March 2018 and identifies a Takamisawa instrument; the separate TFD.011 row is marked through February 2018 and is not substituted for it.'],
  ['1310750', '東京墨田区東向島', 'Higashimukojima', 'TFD.0139190', '向島消防署', 'Mukojima Fire Station', '東京都墨田区東向島6-22-3', 'The selected row applies from 1 March 2018 and identifies a Takamisawa instrument; the separate TFD.017 row is marked through February 2018.'],
  ['1310950', '東京品川区北品川', 'Kitashinagawa', 'TFD.0139150', '品川消防署', 'Shinagawa Fire Station', '東京都品川区北品川3-7-31', 'The selected row applies from 1 March 2018 and identifies a Takamisawa instrument; the separate TFD.012 row is marked through February 2018.'],
];

for (const [code, nameJa, english, inventoryId, host, facilityNameEn, address, qualification] of updates) {
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
  row.note += ` Placement evidence checked on 17 October 2026: the public SK-net Tokyo Fire Department observation inventory, maintained by the University of Tokyo Earthquake Research Institute, explicitly names ${host} in the installation-facility column for ${inventoryId}. Its heading states last updated 11 January 2022. The complete selected row and column headings were read, without logging in or obtaining restricted waveform data. ${qualification} Tokyo metropolitan government's current police/fire contact table independently lists the named fire station and its address, recorded here as ${address}. The instrument host comes from the explicit installation inventory, not from the contact table, a jurisdiction list or proximity to the catalogue point. Association with this reporting record uses the named facility and its independently published matching locality; neither source is claimed to print a seven-digit JMA-code crosswalk. The dated inventory is retained as a source-age qualification, not a fresh 2026 instrument inspection or complete operational-continuity recertification. No particular room, floor, exact within-site sensor position, new installation date or physical visit is asserted. Facility English is descriptive, not claimed as an official English title; approved station names and coordinates are unchanged.`;
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
console.log(`${apply ? 'Applied' : 'Would apply'} six dated-inventory Tokyo fire-host addresses; all protected fields, other records, row order and previous provenance preserved; zero address/precision mismatches. Coverage delta only: exact +6, municipality/ward -6, remaining facility/address research -6; facility-only unchanged.`);
