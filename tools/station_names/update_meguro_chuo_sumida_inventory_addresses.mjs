import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_tocho.html';
const updates = [
  {
    code: '1311030', nameJa: '東京目黒区中央町', english: 'Chuocho', inventoryId: 'TKY.1100',
    facilityNameJa: '目黒区防災センター', facilityNameEn: 'Meguro City Disaster Prevention Center',
    address: '東京都目黒区中央町一丁目9番7号',
    addressUrl: 'https://www.city.meguro.tokyo.jp/dourokouen/shisetsu/sonota/doboku.html',
    evidence: 'The public SK-net Tokyo metropolitan observation inventory, maintained by the University of Tokyo Earthquake Research Institute, explicitly names 目黒区防災センター in the installation-facility column for TKY.1100, Meguro Ward. The official municipal facility guide independently places the Roads and Parks Service Office within the Disaster Prevention Center and publishes 目黒区中央町一丁目9番7号. The center identity comes from the instrument inventory, not from that office merely sharing the Chuocho locality. The office guide refers to its own basement premises; it does not locate the seismometer in the basement.',
  },
  {
    code: '1310230', nameJa: '東京中央区築地', english: 'Tsukiji', inventoryId: 'TKY.1020',
    facilityNameJa: '中央区役所', facilityNameEn: 'Chuo City Office',
    address: '東京都中央区築地一丁目1番1号',
    addressUrl: 'https://www.city.chuo.lg.jp/a0006/kusei/gaiyou/kuyakusho/ku.html',
    evidence: 'The public SK-net Tokyo metropolitan observation inventory, maintained by the University of Tokyo Earthquake Research Institute, explicitly names 中央区役所 in the installation-facility column for TKY.1020, Chuo Ward. The official ward-office facility guide, published 15 April 2026, independently publishes 東京都中央区築地一丁目1番1号. The official main-office redevelopment page states that the present building remains in use while future replacement is considered; a proposed replacement is not treated as a completed instrument relocation.',
    additionalUrls: ['https://www.city.chuo.lg.jp/a0006/kusei/kousoukeikaku/sonota/shisetsuseibi/hontyousyaseibi.html'],
  },
  {
    code: '1310730', nameJa: '東京墨田区吾妻橋', english: 'Azumabashi', inventoryId: 'TKY.1070',
    facilityNameJa: '墨田区役所', facilityNameEn: 'Sumida City Office',
    address: '東京都墨田区吾妻橋一丁目23番20号',
    addressUrl: 'https://www.city.sumida.lg.jp/sisetu_info/kuyakusyo/sumidakuyakusyo.html',
    evidence: 'The public SK-net Tokyo metropolitan observation inventory, maintained by the University of Tokyo Earthquake Research Institute, explicitly names 墨田区役所 in the installation-facility column for TKY.1070, Sumida Ward. The official ward-office access guide, updated 1 April 2026, independently publishes 東京都墨田区吾妻橋一丁目23番20号. This host evidence concerns the metropolitan observation network, not the separate Building Research Institute multi-floor building-response instruments. The guide also announces first/second-floor and Riverside Hall renovation; it does not identify a sensor floor or establish any sensor movement.',
  },
];

for (const update of updates) {
  const row = data.stations.find((station) => station.code === update.code);
  assert.ok(row, `Missing ${update.code}`);
  assert.equal(row.nameJa, update.nameJa);
  assert.equal(row.approvedEnglishName, update.english);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa, `Existing placement ${update.code}`);
  Object.assign(row, {
    facilityNameJa: update.facilityNameJa,
    facilityNameEn: update.facilityNameEn,
    publishedAddressJa: update.address,
    placementLocalityJa: update.address,
    placementPrecision: 'exact_address',
    metadataStatus: 'Public university installation inventory (2021) and independently published official facility address',
  });
  row.note += ` Placement evidence checked on 17 October 2026: ${update.evidence} Inventory heading: last updated 2 January 2021. The complete selected row and its column headings were read on the public installation-information page without logging in or obtaining restricted waveform data. The inventory names a host, while the official municipal guide supplies the address; no street address is inferred from coordinates or proximity. Matching ${update.inventoryId} to this reporting record is an evidence-based identity association through the named host and published station locality, not a source-printed seven-digit-code crosswalk. The inventory date is retained as a source-age qualification: this is not a new 2026 verification of the installed instrument or a full operational-continuity recertification. No exact sensor position, room, floor, installation date or physical visit is asserted. Facility English is descriptive, not claimed as an official English title. Approved station names, coordinates and existing provenance remain unchanged.`;
  for (const url of [inventory, update.addressUrl, ...(update.additionalUrls ?? [])]) {
    if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
  }
}

const codes = new Set(updates.map((update) => update.code));
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
console.log(`${apply ? 'Applied' : 'Would apply'} three dated-inventory host/address updates; all protected fields, other records, row order and existing provenance preserved; zero address/precision mismatches. Coverage delta only: exact +3, municipality/ward -3, remaining facility/address research -3; facility-only unchanged.`);
