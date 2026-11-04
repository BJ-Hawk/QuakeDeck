import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html';
const updates = [
  ['1121430', '春日部市金崎', 'Kanasaki', 'SIT.092', '春日部市庄和総合支所', 'Kasukabe City Showa General Branch Office', '埼玉県春日部市金崎839番地1', 'https://www.city.kasukabe.lg.jp/shiseijoho/shinogaiyo/shiyakusho_sogoshishohenoaccess/2/14584.html', 'The official Showa General Branch facility guide, updated 1 November 2025, explicitly publishes Kanasaki 839-1. This is the branch-office host, not Showa Fire Station elsewhere in Kanasaki or the main City Hall address in Chuo. Shared library/children\'s-center functions do not establish a sensor room.'],
  ['1121440', '春日部市谷原新田', 'Yaharashinden', 'SIT.014', '春日部市消防本部', 'Kasukabe City Fire Headquarters', '埼玉県春日部市谷原新田2097番地1', 'https://www.city.kasukabe.lg.jp/benrinavi/shisetsuannai/categorykarasagasu/10/10540.html', 'The direct official Fire Headquarters facility guide, updated 13 January 2022, explicitly publishes Yaharashinden 2097-1. The guide body was retrieved directly after the web-reader endpoint errored. This is not a guessed parcel, and the co-located disaster-center name does not locate the instrument within a building.'],
  ['1120333', '川口市安行領家', 'Angyo Ryoke', 'SIT.003', '川口市東消防署安行分署', 'Kawaguchi City East Fire Station Angyo Branch', '埼玉県川口市安行領家968', 'https://www.city.kawaguchi.lg.jp/soshiki/01040/010/7/5/2854.html', 'The official sandbag-distribution facility table, updated 1 April 2026, explicitly lists 安行消防分署 at 川口市安行領家968 with telephone 048-296-1110. That table is used only for the facility address; the earthquake-instrument host comes separately from the installation inventory. The official East Fire Station jurisdiction page, updated 15 May 2025, independently confirms the same Angyo branch name/telephone under East Fire Station; jurisdiction is used only to corroborate the parent organization, never to infer a sensor placement. The table\'s temporary-relocation footnote explicitly concerns Shingo Branch, not Angyo. Neither the parent station\'s Sakashitacho address nor the municipal Angyo service-office address is substituted.'],
  ['1120331', '川口市三ツ和', 'Mitsuwa', 'SIT.026', '川口市鳩ヶ谷支所', 'Kawaguchi City Hatogaya Branch Office', '埼玉県川口市三ツ和1-14-3', 'https://www.city.kawaguchi.lg.jp/soshiki/01060/120/index.html', 'The official Hatogaya Branch page explicitly publishes Mitsuwa 1-14-3 within the Hatogaya municipal building. The separate current building guide independently publishes that site address and still lists Hatogaya Branch there. Its notice that some departments moved to the main hall on 21 December 2025 is not a statement that this branch or the instrument moved. The different Aoki mailing/main-hall address is not used. The office\'s first-floor designation is not assigned to the instrument.'],
  ['1120731', '秩父市吉田', 'Yoshida', 'SIT.062', '秩父市吉田総合支所', 'Chichibu City Yoshida General Branch Office', '埼玉県秩父市下吉田6585-2', 'https://www.city.chichibu.lg.jp/1034.html', 'The current official Yoshida General Branch guide explicitly names the branch and publishes Shimoyoshida 6585-2. The inventory\'s former-municipality/change-date columns describe municipal history, not an instrument installation or relocation date. No proposed future functional consolidation is treated as a completed branch or sensor transfer.'],
  ['1120733', '秩父市荒川', 'Arakawa', 'SIT.066', '秩父市荒川総合支所', 'Chichibu City Arakawa General Branch Office', '埼玉県秩父市荒川上田野1734-6', 'https://www.city.chichibu.lg.jp/1036.html', 'The current official Arakawa General Branch guide explicitly names the branch and publishes Arakawa Kamitano 1734-6. The guide\'s notices of water-utility, waste-policy and education-consultation office moves do not state a branch or instrument transfer. Their destinations, the main-hall footer and the floor assignments of unrelated offices are not used as sensor placement evidence.'],
  ['1120735', '秩父市大滝', 'Otaki', 'SIT.065', '秩父市大滝総合支所', 'Chichibu City Otaki General Branch Office', '埼玉県秩父市大滝4058', 'https://www.city.chichibu.lg.jp/1035.html', 'The current official Otaki General Branch guide explicitly names the branch and publishes Otaki 4058. Co-located health-center/accounting functions do not locate the instrument in a room or floor. The inventory\'s municipal-merger history is not an instrument move date.'],
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
  const additional = code === '1120333' ? ['https://www.city.kawaguchi.lg.jp/soshiki/05010/0800/minamiukemotikuiki/41994.html'] : code === '1120331' ? ['https://www.city.kawaguchi.lg.jp/soshiki/01090/010/hatogayatyousyakanri/41959.html'] : [];
  for (const url of [inventory, addressSource, ...additional]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}
const codes = new Set(updates.map(([code]) => code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(codes.size, 7);
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
console.log(`${apply ? 'Applied' : 'Would apply'} seven Saitama fire/branch host addresses; protected fields, unrelated rows, order and previous provenance preserved; zero address/precision mismatches. Coverage delta only: exact +7, municipality/ward -7, remaining facility/address research -7; facility-only unchanged.`);
