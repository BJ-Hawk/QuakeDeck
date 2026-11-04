import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html';
const updates = [
  ['1132430', '埼玉三芳町藤久保', 'Fujikubo', 'SIT.047', '三芳町役場', 'Miyoshi Town Hall', '埼玉県入間郡三芳町大字藤久保1100番地1', 'https://www.town.saitama-miyoshi.lg.jp/town/yakuba/kaichou_jikan_access.html'],
  ['1132630', '毛呂山町中央', 'Chuo', 'SIT.048', '毛呂山町役場', 'Moroyama Town Hall', '埼玉県入間郡毛呂山町中央2丁目1番地', 'https://www.town.moroyama.saitama.jp/soshikikarasagasu/somuka/shouboubousai/2209.html'],
  ['1132730', '越生町越生', 'Ogose', 'SIT.049', '越生町役場', 'Ogose Town Hall', '埼玉県入間郡越生町大字越生900番地2', 'https://www.town.ogose.saitama.jp/chousei/gaiyou/1453339792341.html'],
  ['1134130', '滑川町福田', 'Fukuda', 'SIT.051', '滑川町役場', 'Namegawa Town Hall', '埼玉県比企郡滑川町大字福田750番地1', 'https://www.town.namegawa.saitama.jp/soshikikarasagasu/somuseisakuka/namegawamachinogaiyo/1/745.html'],
  ['1134730', '吉見町下細谷', 'Shimohosoya', 'SIT.057', '吉見町役場', 'Yoshimi Town Hall', '埼玉県比企郡吉見町大字下細谷411番地', 'https://www.town.yoshimi.saitama.jp/gyosei/3/3/index.html'],
  ['1136230', '皆野町皆野', 'Minano', 'SIT.060', '皆野町役場', 'Minano Town Hall', '埼玉県秩父郡皆野町大字皆野1420番地の1', 'https://www.town.minano.saitama.jp/reiki_int/reiki_honbun/e361RG00000001.html'],
  ['1136330', '長瀞町本野上', 'Honnogami', 'SIT.061', '長瀞町役場', 'Nagatoro Town Hall', '埼玉県秩父郡長瀞町大字本野上1035番地1', 'https://www.town.nagatoro.saitama.jp/nagatoro-2/acessmap/'],
  ['1146430', '杉戸町清地', 'Seiji', 'SIT.090', '杉戸町役場', 'Sugito Town Hall', '埼玉県北葛飾郡杉戸町清地2丁目9番29号', 'https://www.town.sugito.lg.jp/'],
  ['1146530', '松伏町松伏', 'Matsubushi', 'SIT.091', '松伏町役場', 'Matsubushi Town Hall', '埼玉県北葛飾郡松伏町大字松伏2424番地', 'https://www.town.matsubushi.lg.jp/0000001490.html'],
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
    metadataStatus: 'Public university installation inventory (2026) and independently published official town-office address' });
  let evidence = 'The official town-office/access page independently publishes the named office site address.';
  if (code === '1132630') evidence = 'The official town facility-address table explicitly identifies Town Hall at Chuo 2-1, independently repeated in the town office contact block as Chuo 2丁目1番地. That table concerns AEDs and is used only for the facility address; the earthquake-instrument host relationship comes separately from the installation inventory, not the AED or its position.';
  if (code === '1134130') evidence = 'The official public-facility directory, updated 1 September 2026, explicitly names Town Hall at Fukuda 750-1. Other facilities sharing the parcel are not substituted as the instrument host.';
  if (code === '1136230') evidence = 'The official town-office location ordinance explicitly publishes Minano 1420-1; the current municipal homepage independently repeats the same Town Hall address. The ordinance date is not an instrument installation date.';
  if (code === '1146430') evidence = 'The current official municipal homepage explicitly identifies Sugito Town Hall at Seiji 2-9-29. This is a published host-site address, not an assignment to a particular office wing or a future replacement building.';
  row.note += ` Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Saitama inventory explicitly names ${host} in the installation-facility column for ${id}; its heading states last updated 16 May 2026. The complete selected row and column headings were read without logging in or obtaining restricted waveform data. ${evidence} Recorded address: ${address}. Association uses the explicit host identity and independently published matching locality, not a generic town-office installation rule, coordinates, proximity or a source-printed seven-digit JMA-code crosswalk. The inventory page date is provenance, not an individual physical sensor inspection or full operational-continuity recertification. No room, floor, precise within-site sensor position, installation/transfer date or physical visit is asserted. English facility names are descriptive; approved station names, coordinates and previous provenance remain unchanged.`;
  for (const url of [inventory, addressSource, ...(code === '1136230' ? ['https://www.town.minano.saitama.jp/'] : [])]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}
const codes = new Set(updates.map(([code]) => code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(codes.size, 9);
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
console.log(`${apply ? 'Applied' : 'Would apply'} nine Saitama town-host addresses; protected fields, other rows, order and provenance preserved; zero address/precision mismatches. Coverage delta only: exact +9, municipality/ward -9, remaining facility/address research -9; facility-only unchanged.`);
