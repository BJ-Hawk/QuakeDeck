import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html';
const directory = 'https://www.metro.tokyo.lg.jp/tosei/iken-sodan/sodan/kesatsu';
const updates = [
  ['1321351', '東村山市美住町', 'Misumicho', 'TFD.008', '東村山消防署', 'Higashimurayama Fire Station', '東京都東村山市美住町2-28-16', directory],
  ['1320952', '町田市本町田', 'Hommachida', 'TFD.009', '町田消防署', 'Machida Fire Station', '東京都町田市本町田2380-3', directory],
  ['1311850', '東京荒川区東尾久', 'Higashiogu', 'TFD.025', '尾久消防署', 'Ogu Fire Station', '東京都荒川区東尾久8-44-4', directory],
  ['1312351', '東京江戸川区鹿骨', 'Shishibone', 'TFD.027', '小岩消防署', 'Koiwa Fire Station', '東京都江戸川区鹿骨2-42-11', directory],
  ['1322152', '清瀬市中清戸', 'Nakakiyoto', 'TFD.029', '清瀬消防署', 'Kiyose Fire Station', '東京都清瀬市中清戸2-850-1', directory],
  ['1311951', '東京板橋区相生町', 'Aioicho', 'TFD.036', '志村消防署', 'Shimura Fire Station', '東京都板橋区相生町17-1', directory],
  ['1311950', '東京板橋区板橋', 'Itabashi', 'TFD.0139070', '板橋消防署', 'Itabashi Fire Station', '東京都板橋区板橋2-60-15', directory],
  ['1321852', '福生市福生', 'Fussa', 'TFD.0139020', '福生消防署', 'Fussa Fire Station', '東京都福生市福生1072', directory],
  ['1312252', '東京葛飾区金町', 'Kanamachi', 'TFD.0139210', '金町消防署', 'Kanamachi Fire Station', '東京都葛飾区金町4-15-20', directory],
  ['1311152', '東京大田区本羽田', 'Honhaneda', 'TFD.022', '蒲田消防署羽田出張所', 'Kamata Fire Station Haneda Branch', '東京都大田区本羽田三丁目1番2号', 'https://www.tfd.metro.tokyo.lg.jp/fs/kamata/index.html'],
  ['1311451', '東京中野区江古田', 'Egota', 'TFD.034', '野方消防署江古田出張所', 'Nogata Fire Station Egota Branch', '東京都中野区江古田二丁目2番7号', 'https://www.tfd.metro.tokyo.lg.jp/fs/nogata/index.html'],
  ['1310854', '東京江東区枝川', 'Edagawa', 'TFD.0139200', '深川消防署枝川出張所', 'Fukagawa Fire Station Edagawa Branch', '東京都江東区枝川三丁目2番1号', 'https://www.tfd.metro.tokyo.lg.jp/fs/fukagawa/index.html'],
  ['1320352', '武蔵野市吉祥寺東町', 'Kichijoji Higashicho', 'TFD.0139220', '武蔵野消防署吉祥寺出張所', 'Musashino Fire Station Kichijoji Branch', '東京都武蔵野市吉祥寺東町一丁目1番21号', 'https://www.tfd.metro.tokyo.lg.jp/fs/musasino/index.html'],
];
for (const [code, nameJa, english, inventoryId, host, facilityNameEn, address, addressSource] of updates) {
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
    metadataStatus: 'Public university fire-service installation inventory (2022) and independently published official facility address',
  });
  let qualification = 'The selected inventory row identifies a Meisei instrument.';
  if (inventoryId === 'TFD.0139070') qualification = 'The selected row identifies a Takamisawa instrument and the pre-21-February-2012 identifier 0550810; identifier history is not a physical relocation date.';
  if (inventoryId === 'TFD.0139020') qualification = 'The selected row identifies a Takamisawa instrument and the pre-21-February-2012 identifier 0591910; identifier history is not a physical relocation date.';
  const olderRows = { 'TFD.0139210': 'TFD.019', 'TFD.0139200': 'TFD.018', 'TFD.0139220': 'TFD.020' };
  if (olderRows[inventoryId]) qualification = `The selected Takamisawa row applies from 1 March 2018; the separate ${olderRows[inventoryId]} Meisei row is explicitly marked through February 2018 and is not substituted for it.`;
  const addressEvidence = addressSource === directory
    ? 'Tokyo metropolitan government\'s police/fire contact table independently lists the named main fire station and its address.'
    : 'The current Tokyo Fire Department station access section independently names the specific branch and publishes its address; the parent main station\'s different address is not substituted.';
  row.note += ` Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Tokyo Fire Department observation inventory explicitly names ${host} in the installation-facility column for ${inventoryId}. Its heading states last updated 11 January 2022. The complete selected row and column headings were read without logging in or retrieving restricted waveform data. ${qualification} ${addressEvidence} The published host-site address is recorded as ${address}. The instrument host comes from the installation inventory, not a generic fire-station policy, jurisdiction list, contact address alone, coordinates or proximity. Association with this reporting record uses the explicit host identity and independently published matching locality; no source is claimed to print the seven-digit JMA-code crosswalk. The dated inventory is retained as a source-age qualification, not a fresh 2026 instrument inspection or full operational-continuity recertification. No room, floor, precise within-site sensor position, physical visit or installation/transfer date is asserted. Facility English is descriptive, not claimed as an official English title; approved Japanese and English station names, coordinates and earlier provenance remain unchanged.`;
  for (const url of [inventory, addressSource]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}

const codes = new Set(updates.map(([code]) => code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(codes.size, 13);
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
console.log(`${apply ? 'Applied' : 'Would apply'} thirteen dated-inventory Tokyo fire-host addresses; all protected fields, other records, row order and previous provenance preserved; zero address/precision mismatches. Coverage delta only: exact +13, municipality/ward -13, remaining facility/address research -13; facility-only unchanged.`);
