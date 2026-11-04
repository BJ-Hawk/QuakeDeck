import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_tocho.html';
const updates = [
  {
    code: '1310432', nameJa: '東京新宿区歌舞伎町', english: 'Kabukicho', id: 'TKY.1040',
    inventoryHost: '新宿区役所', facilityNameJa: '新宿区役所', facilityNameEn: 'Shinjuku City Office',
    address: '東京都新宿区歌舞伎町一丁目4番1号',
    addressSource: 'https://www.city.shinjuku.lg.jp/kusei/kuseijoho01_001068.html',
    evidence: 'The official ward-office access guide, updated 18 May 2026, independently publishes the main office site at Kabukicho 1-4-1 and separately distinguishes the first annex at 1-5-1 and second annex in Shinjuku 5-18-21. The named ward-office site address is retained without assigning the instrument to a floor, room or wing.',
  },
  {
    code: '1311931', nameJa: '東京板橋区高島平', english: 'Takashimadaira', id: 'TKY.1190',
    inventoryHost: '高島平区民事務所', facilityNameJa: '板橋区高島平区民事務所', facilityNameEn: 'Itabashi City Takashimadaira Branch Office',
    address: '東京都板橋区高島平3丁目12番28号',
    addressSource: 'https://www.city.itabashi.tokyo.jp/tetsuduki/koseki/madoguchi/madoguchi/1033679/1001711.html',
    evidence: 'The official branch-office guide, updated 1 April 2026, independently names the Takashimadaira office and publishes Takashimadaira 3-12-28. The address is not that of Itabashi City Office or a nearby police/fire station.',
  },
  {
    code: '1312230', nameJa: '東京葛飾区立石', english: 'Tateishi', id: 'TKY.1220',
    inventoryHost: '葛飾区役所', facilityNameJa: '葛飾区役所', facilityNameEn: 'Katsushika City Office',
    address: '東京都葛飾区立石5-13-1',
    addressSource: 'https://www.city.katsushika.lg.jp/institution/public/office.html',
    evidence: 'The official Katsushika general-office facility guide, updated 9 April 2026, independently publishes Tateishi 5-13-1. This is the existing named office site, not a proposed future replacement building or an instrument location derived from an AED position.',
  },
  {
    code: '1320631', nameJa: '東京府中市寿町', english: 'Kotobukicho', id: 'TKY.2060',
    inventoryHost: '府中市中央防災センター', facilityNameJa: '府中市中央防災センター', facilityNameEn: 'Fuchu City Central Disaster Prevention Center',
    address: '東京都府中市寿町1丁目5番地',
    addressSource: 'https://www.city.fuchu.tokyo.jp/cp_fuchu/shisetu/chosha/thuobosaisenta.html',
    additionalUrls: ['https://www.city.fuchu.tokyo.jp/shisetu/chosha/index.html'],
    evidence: 'The official Central Disaster Prevention Center guide independently publishes Kotobukicho 1-5 and describes co-location with Fuchu Fire Station. The current municipal office-directory table independently repeats that center address. The host is the explicitly inventoried municipal center, not an inferred fire-station sensor and not the main City Hall in Miyanishicho.',
  },
  {
    code: '1320730', nameJa: '昭島市田中町', english: 'Tanakacho', id: 'TKY.2070',
    inventoryHost: '昭島市役所', facilityNameJa: '昭島市役所', facilityNameEn: 'Akishima City Hall',
    address: '東京都昭島市田中町1-17-1',
    addressSource: 'https://www.city.akishima.lg.jp/shisei/shisetsu/1008430/1007669.html',
    evidence: 'The official City Hall facility guide, updated 19 March 2026, independently publishes Tanakacho 1-17-1 and states that the office has operated at the current site since May 1997. That hall-opening date is not asserted as an instrument installation or transfer date.',
  },
  {
    code: '1321331', nameJa: '東村山市本町', english: 'Honcho', id: 'TKY.2130',
    inventoryHost: '東村山市役所市民センター', facilityNameJa: '東村山市市民センター', facilityNameEn: 'Higashimurayama City Civic Center',
    address: '東京都東村山市本町1丁目1番地1',
    addressSource: 'https://www.restanet-tokyo.metro.tokyo.lg.jp/result/0207/',
    additionalUrls: ['https://www.rodosya-kyodo-k.metro.tokyo.lg.jp/seminar', 'https://www.city.kodaira.tokyo.jp/shihou/116/116604.html'],
    evidence: 'Tokyo metropolitan government independently publishes a support office within the Civic Center at Honcho 1-1-1. Its official 17 March 2025 seminar listing independently names the Civic Center and repeats the full address; Kodaira City\'s 5 December 2024 newsletter also identifies that venue at Honcho 1-1-1. The support-office floor is not assigned to the instrument. The inventory\'s 市役所市民センター wording is retained as source context; the formal facility identity is 市民センター, not a guessed City Hall wing.',
  },
  {
    code: '1322030', nameJa: '東大和市中央', english: 'Chuo', id: 'TKY.2200',
    inventoryHost: '東大和市役所', facilityNameJa: '東大和市役所', facilityNameEn: 'Higashiyamato City Hall',
    address: '東京都東大和市中央3-930',
    addressSource: 'https://www.city.higashiyamato.lg.jp/shisei/shiyakusho/shisetsu/city/1006823.html',
    evidence: 'The official City Hall facility guide, updated 3 April 2025, independently publishes Chuo 3-930 and states that the office moved there from Narahashi in 1982. The former Narahashi office and adjacent library/community-center functions are not substituted for the inventoried City Hall host; the building move date is not a meter-transfer date.',
  },
  {
    code: '1336330', nameJa: '新島村本村', english: 'Honson', id: 'TKY.3630',
    inventoryHost: '新島村役場', facilityNameJa: '新島村役場', facilityNameEn: 'Niijima Village Office',
    address: '東京都新島村本村1-1-1',
    addressSource: 'https://www.niijima.com/gyousei/chousha_yakuba/yakubaeigyouannai.html',
    evidence: 'The official village-office operating guide independently names the main office at Honson 1-1-1 and distinguishes the Shikinejima and Wakago branches at other addresses. The main-office site address is recorded without assigning a room, floor or precise within-site instrument position.',
  },
];
for (const u of updates) {
  const row = data.stations.find((station) => station.code === u.code);
  assert.ok(row, `Missing ${u.code}`);
  assert.equal(row.nameJa, u.nameJa);
  assert.equal(row.approvedEnglishName, u.english);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa, `Existing placement ${u.code}`);
  Object.assign(row, {
    facilityNameJa: u.facilityNameJa,
    facilityNameEn: u.facilityNameEn,
    publishedAddressJa: u.address,
    placementLocalityJa: u.address,
    placementPrecision: 'exact_address',
    metadataStatus: 'Public university installation inventory (2021) and independently published official facility address',
  });
  row.note += ` Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Tokyo metropolitan observation inventory explicitly names ${u.inventoryHost} in the installation-facility column for ${u.id}. Its heading states last updated 2 January 2021; the link to old observation information is a separate page, not the selected inventory. The complete selected row and column headings were read without logging in or obtaining restricted waveform data. ${u.evidence} The published host-site address is recorded as ${u.address}. The instrument host comes from the installation inventory, not the facility address alone, generic municipal policy, rounded coordinates or proximity. Matching this reporting record uses the explicit host identity and independently published locality, not a claimed source-printed seven-digit JMA-code crosswalk. The dated inventory is retained as a source-age qualification, not a fresh 2026 instrument inspection or full operational-continuity recertification. No exact sensor position, room, floor, installation date or physical visit is asserted. Facility English is descriptive, not claimed as an official English title; approved Japanese and English station names, coordinates and existing provenance remain unchanged.`;
  for (const url of [inventory, u.addressSource, ...(u.additionalUrls ?? [])]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}

const codes = new Set(updates.map((u) => u.code));
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
console.log(`${apply ? 'Applied' : 'Would apply'} eight dated-inventory Tokyo municipal-host addresses; all protected fields, other records, row order and previous provenance preserved; zero address/precision mismatches. Coverage delta only: exact +8, municipality/ward -8, remaining facility/address research -8; facility-only unchanged.`);
