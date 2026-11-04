import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html';
const updates = [
  ['1134631', '川島町下八ツ林', 'Shimoyatsubayashi', 'SIT.056', '川島町役場', 'Kawajima Town Hall', '埼玉県比企郡川島町大字下八ツ林870番地1', 'https://www.town.kawajima.saitama.jp/1837.htm', 'The official facility guide explicitly distinguishes Town Hall at Shimoyatsubayashi 870-1 from the separately listed Health Center at Hiranuma 1175. No former-office parcel or floor-plan room is substituted for the named host.'],
  ['1138130', '埼玉美里町木部', 'Kibe', 'SIT.068', '美里町役場', 'Misato Town Hall', '埼玉県児玉郡美里町大字木部323番地1', 'https://www.town.saitama-misato.lg.jp/', 'The current official municipal homepage explicitly names Misato Town Hall and publishes Kibe 323-1 in its office-address block. The address is used for the separately inventoried host, not inferred from a generic town-office installation policy.'],
  ['1138333', '埼玉神川町植竹', 'Uedake', 'SIT.070', '神川町役場', 'Kamikawa Town Hall', '埼玉県児玉郡神川町大字植竹909', 'https://www.town.kamikawa.saitama.jp/soshiki/866.html', 'The official public-facility list explicitly identifies Town Hall at Uedake 909, separately from Shinsen General Branch at Shimoaguhara 1088, the employment-improvement center at Uedake 900-1 and the community center at Uedake 867-2. No parcel suffix is invented.'],
  ['1134930', 'ときがわ町桃木', 'Momonoki', 'SIT.054', 'ときがわ町役場第二庁舎', 'Tokigawa Town Office Second Building', '埼玉県比企郡ときがわ町大字桃木32番地', 'https://www.town.tokigawa.lg.jp/info/293', 'The official Second Building guide, updated 3 April 2023, explicitly publishes Momonoki 32. Its separate general-affairs contact footer at Tamagawa 2490 is not used as this host address. The inventory municipality label retains the former-area wording ときがわ町都幾川; the explicit Second Building identity is paired with the independent published Momonoki locality, not a coordinate match.'],
  ['1134931', 'ときがわ町玉川', 'Tamagawa', 'SIT.055', 'ときがわ町役場本庁舎', 'Tokigawa Town Office Main Building', '埼玉県比企郡ときがわ町大字玉川2490番地', 'https://www.town.tokigawa.lg.jp/info/292', 'The direct official Main Building guide, updated 3 April 2023, explicitly publishes Tamagawa 2490 in its facility-address table. This is not a promotion based solely on another facility page\'s department-contact footer; the main and second buildings are independently distinguished.'],
  ['1123930', '坂戸市千代田', 'Chiyoda', 'SIT.039', '坂戸市役所', 'Sakado City Hall', '埼玉県坂戸市千代田1-1-1', 'https://www.city.sakado.lg.jp/soshiki/2/613.html', 'The official City Hall opening guide, updated 1 October 2026, publishes Chiyoda 1-1-1 in its office-contact block. The address identifies the separately inventoried City Hall site, not the instrument\'s department, floor or room.'],
  ['1124532', 'ふじみ野市大井', 'Oi', 'SIT.046', 'ふじみ野市大井総合支所', 'Fujimino City Oi General Branch Office', '埼玉県ふじみ野市大井中央1-1-1', 'https://www.city.fujimino.saitama.jp/soshikiichiran/shiminsogomadoguchika/somukakari/1935.html', 'The official Oi General Branch guide, updated 19 June 2026, explicitly publishes Oichuo 1-1-1 in the named branch facility table. The different main City Hall footer address at Fukuoka 1-1-1 is not used; the approved station label Oi is preserved.'],
  ['1121734', '鴻巣市吹上富士見', 'Fukiagefujimi', 'SIT.045', '鴻巣市吹上支所', 'Konosu City Fukiage Branch Office', '埼玉県鴻巣市吹上富士見1-1-1', 'https://www.city.kounosu.saitama.jp/page/1041.html', 'The official branch guide, updated 28 February 2023, identifies Fukiage Branch within the コスモスふきあげ館 complex and explicitly publishes Fukiagefujimi 1-1-1. The page\'s January 2017 water/sewer department relocation is not asserted to be an instrument move, and the main City Hall footer at Chuo 1-1 is not substituted.'],
];

for (const [code, nameJa, english, id, facilityNameJa, facilityNameEn, address, addressSource, evidence] of updates) {
  const row = data.stations.find((station) => station.code === code);
  assert.ok(row, `Missing ${code}`);
  assert.equal(row.nameJa, nameJa);
  assert.equal(row.approvedEnglishName, english);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa, `Existing placement ${code}`);
  Object.assign(row, { facilityNameJa, facilityNameEn, publishedAddressJa: address,
    placementLocalityJa: address, placementPrecision: 'exact_address',
    metadataStatus: 'Public university installation inventory (2026) and independently published official facility address' });
  row.note += ` Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Saitama inventory explicitly names ${facilityNameJa} in the installation-facility column for ${id}; its heading states last updated 16 May 2026. The complete selected row and column headings were read without logging in or retrieving restricted waveform data. ${evidence} Recorded host-site address: ${address}. Association uses the explicit host identity and independently published matching locality, not coordinates, proximity, a generic municipality policy or a claimed source-printed seven-digit JMA-code crosswalk. The inventory page date is provenance, not an individual physical instrument inspection or full operational-continuity recertification. No floor, room, precise within-site sensor position, installation/transfer date or physical visit is asserted. English facility names are descriptive; approved station names, coordinates and previous provenance remain unchanged.`;
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
console.log(`${apply ? 'Applied' : 'Would apply'} eight Saitama town/branch host addresses; all protected fields, unrelated rows, order and provenance preserved; zero address/precision mismatches. Coverage delta only: exact +8, municipality/ward -8, remaining facility/address research -8; facility-only unchanged.`);
