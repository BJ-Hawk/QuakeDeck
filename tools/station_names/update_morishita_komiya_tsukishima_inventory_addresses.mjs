import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html';
const updates = [
  {
    code: '1310850', nameJa: '東京江東区森下', english: 'Morishita', id: 'TFD.0139090',
    inventoryHost: '深川署森下派出所', facilityNameJa: '東京消防庁深川消防署森下出張所',
    facilityNameEn: 'Fukagawa Fire Station Morishita Branch', address: '東京都江東区森下五丁目1番4号',
    addressSource: 'https://www.tfd.metro.tokyo.lg.jp/fs/fukagawa/index.html',
    qualification: 'The inventory uses the abbreviated/older wording 深川署森下派出所. The current fire-service access section explicitly identifies Morishita Branch under Fukagawa Fire Station and publishes its Morishita 5-1-4 address. The co-located regional headquarters is not substituted for the branch host. The inventory identifies a Takamisawa instrument and its pre-21-February-2012 identifier 0400700; identifier history is not a physical relocation date.',
  },
  {
    code: '1320150', nameJa: '八王子市石川町', english: 'Ishikawamachi', id: 'TFD.0139110',
    inventoryHost: '八王子署小宮出張所', facilityNameJa: '東京消防庁八王子消防署小宮出張所',
    facilityNameEn: 'Hachioji Fire Station Komiya Branch', address: '東京都八王子市石川町2099番2号',
    addressSource: 'https://www.tfd.metro.tokyo.lg.jp/fs/hatiouji/index.html',
    qualification: 'The current fire-service access section independently identifies Komiya Branch under Hachioji Fire Station and publishes Ishikawamachi 2099-2. The branch name is not mistaken for a Komiyamachi street address. The inventory identifies a Takamisawa instrument and its pre-21-February-2012 identifier 0400900; identifier history is not a physical relocation date.',
  },
  {
    code: '1310253', nameJa: '東京中央区勝どき', english: 'Kachidoki', id: 'TFD.031',
    inventoryHost: '臨港消防署月島出張所', facilityNameJa: '東京消防庁臨港消防署月島出張所',
    facilityNameEn: 'Rinko Fire Station Tsukishima Branch', address: '東京都中央区勝どき四丁目6番3号',
    addressSource: 'https://www.tfd.metro.tokyo.lg.jp/fs/rinkou/index.html',
    qualification: 'The inventory identifies a Meisei instrument at Tsukishima Branch. The current fire-service access section independently names that branch and publishes Kachidoki 4-6-3, separately from the Rinko main station in Harumi. The branch name is not substituted for its actual Kachidoki street locality, and the branch jurisdiction list is not the placement evidence.',
  },
];
for (const u of updates) {
  const row = data.stations.find((s) => s.code === u.code);
  assert.ok(row, `Missing ${u.code}`);
  assert.equal(row.nameJa, u.nameJa);
  assert.equal(row.approvedEnglishName, u.english);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa, `Existing placement ${u.code}`);
  Object.assign(row, {
    facilityNameJa: u.facilityNameJa, facilityNameEn: u.facilityNameEn,
    publishedAddressJa: u.address, placementLocalityJa: u.address,
    placementPrecision: 'exact_address',
    metadataStatus: 'Public university fire-service installation inventory (2022) and independently published official branch address',
  });
  row.note += ` Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Tokyo Fire Department observation inventory explicitly names ${u.inventoryHost} in the installation-facility column for ${u.id}. Its heading states last updated 11 January 2022. The complete selected row and column headings were read without logging in or obtaining restricted waveform data. ${u.qualification} The published host-site address is recorded as ${u.address}. The instrument host comes from the installation inventory, not the contact address alone, a generic fire-station policy, coordinates or proximity. Association with this reporting record uses the explicit branch identity and independently published locality; no source is claimed to print a seven-digit JMA-code crosswalk. The dated inventory is retained as a source-age qualification, not a fresh 2026 instrument inspection or full operational-continuity recertification. No room, floor, exact within-site sensor position, installation/transfer date or physical visit is asserted. Facility English is descriptive, not claimed as an official English title; approved Japanese and English station names, coordinates and earlier provenance remain unchanged.`;
  for (const url of [inventory, u.addressSource]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}
const codes = new Set(updates.map((u) => u.code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(codes.size, 3);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((s) => s.code)).size, 4360);
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
console.log(`${apply ? 'Applied' : 'Would apply'} three dated-inventory Tokyo branch-host addresses; all protected fields, other records, row order and previous provenance preserved; zero address/precision mismatches. Coverage delta only: exact +3, municipality/ward -3, remaining facility/address research -3; facility-only unchanged.`);
