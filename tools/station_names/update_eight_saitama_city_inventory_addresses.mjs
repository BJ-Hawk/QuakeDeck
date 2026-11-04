import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html';
const directory = 'https://www.city.saitama.lg.jp/008/001/p002685.html';
const updates = [
  ['1110230', 'さいたま北区宮原', 'Miyaharacho', 'SIT.094', 'さいたま市北区役所', 'Saitama City Kita Ward Office', '埼玉県さいたま市北区宮原町1丁目852番地1', directory],
  ['1110430', 'さいたま見沼区堀崎', 'Horisaki', 'SIT.096', 'さいたま市見沼区役所', 'Saitama City Minuma Ward Office', '埼玉県さいたま市見沼区堀崎町12番地36', directory],
  ['1110530', 'さいたま中央区下落合', 'Shimochiai', 'SIT.020', 'さいたま市中央区役所', 'Saitama City Chuo Ward Office', '埼玉県さいたま市中央区下落合5丁目7番10号', directory],
  ['1110630', 'さいたま桜区道場', 'Dojo', 'SIT.097', 'さいたま市桜区役所', 'Saitama City Sakura Ward Office', '埼玉県さいたま市桜区道場4丁目3番1号', directory],
  ['1110730', 'さいたま浦和区常盤', 'Tokiwa', 'SIT.004', 'さいたま市役所', 'Saitama City Hall', '埼玉県さいたま市浦和区常盤6丁目4番4号', directory],
  ['1110831', 'さいたま南区別所', 'Bessho', 'SIT.098', 'さいたま市立浦和大里小学校', 'Saitama City Urawa Osato Elementary School', '埼玉県さいたま市南区別所7丁目14-28', 'https://urawaosato-e.saitama-city.ed.jp/'],
  ['1110930', 'さいたま緑区中尾', 'Nakao', 'SIT.099', 'さいたま市緑区役所', 'Saitama City Midori Ward Office', '埼玉県さいたま市緑区大字中尾975番地1', directory],
  ['1111031', 'さいたま岩槻区本丸', 'Hommaru', 'SIT.013', '岩槻本丸公民館', 'Iwatsuki Hommaru Community Center', '埼玉県さいたま市岩槻区本丸3-17-1', 'https://www.city.saitama.lg.jp/iwatsuki/001/001/005/001/p066385.html'],
];
for (const [code, nameJa, english, id, host, facilityNameEn, address, addressSource] of updates) {
  const row = data.stations.find((station) => station.code === code);
  assert.ok(row, `Missing ${code}`);
  assert.equal(row.nameJa, nameJa);
  assert.equal(row.approvedEnglishName, english);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa, `Existing placement ${code}`);
  Object.assign(row, { facilityNameJa: host, facilityNameEn, publishedAddressJa: address,
    placementLocalityJa: address, placementPrecision: 'exact_address',
    metadataStatus: 'Public university installation inventory (2026) and independently published official facility address' });
  let qualification = 'The official city ward-office directory independently names the selected office and publishes its full site address.';
  if (code === '1110730') qualification += ' The inventory explicitly names City Hall, not a separate Urawa Ward Office building; no particular wing is assigned.';
  if (code === '1110831') qualification = 'The school\'s current official website independently publishes Bessho 7-14-28. The instrument host is the explicitly inventoried school, not the South Ward Office at the different Bessho 7-20-1 address. School facilities and swimming-pool descriptions do not establish an exact sensor position.';
  if (code === '1111031') qualification = 'The official community-center guide, updated 2 April 2026, independently names Iwatsuki Hommaru Community Center and publishes Hommaru 3-17-1. The relocated Iwatsuki Ward Office in Honcho and the distinct Hommachi Community Center are not substituted for this host.';
  row.note += ` Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Saitama observation inventory explicitly names ${host} in the installation-facility column for ${id}. Its heading states last updated 16 May 2026. The complete selected row and column headings were read, without logging in, importing the catalogue or retrieving restricted waveform data. ${qualification} The published host-site address is recorded as ${address}. Matching this reporting record uses the explicit host identity and independently published locality, not rounded coordinates, proximity, a generic city-office installation policy or a source-printed seven-digit JMA-code crosswalk. The inventory page date is retained as provenance, not asserted as an individual physical instrument inspection or full operational-continuity recertification. No room, floor, precise within-site sensor position, installation/transfer date or physical visit is asserted. Facility English is descriptive, not claimed as an official English title; approved Japanese and English station names, coordinates and previous provenance remain unchanged.`;
  for (const url of [inventory, addressSource]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}
const codes = new Set(updates.map(([code]) => code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(codes.size, 8);
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
console.log(`${apply ? 'Applied' : 'Would apply'} eight Saitama City inventory host-address records; all protected fields, other records, order and previous provenance preserved; zero address/precision mismatches. Coverage delta only: exact +8, municipality/ward -8, remaining facility/address research -8; facility-only unchanged.`);
