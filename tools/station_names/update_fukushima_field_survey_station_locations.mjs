import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = new Map([
  ['0730334', {
    address: '福島県伊達郡国見町大字藤田字一丁田二1番7',
    facility: '国見町役場',
    placementSource: 'https://saigai.aij.or.jp/saigai_info/20220316_fukushima_oki/20220316_fukushima_oki_chousasokuho_TOHOKU_B.pdf',
    addressSource: 'https://www.town.kunimi.fukushima.jp/life/5/23/',
    note: 'The Architectural Institute of Japan’s 2022 field-survey report directly places the Kunimi Town Fujita intensity meter in Kunimi Town Hall’s car park; Kunimi Town publishes the hall’s Japanese address.',
  }],
  ['0756132', {
    address: '福島県相馬郡新地町谷地小屋字樋掛田30',
    facility: '新地町役場',
    placementSource: 'https://www.dpri.kyoto-u.ac.jp/web_j/kokai/potal/r03/r03_ppt5.pdf',
    addressSource: 'https://www.shinchi-town.jp/life/4/27/83/',
    note: 'Kyoto University’s field-survey presentation directly identifies the Shinchi Town Yachigoya intensity meter at Shinchi Town Hall; Shinchi Town publishes the hall’s Japanese address.',
  }],
]);

for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update) continue;
  if (station.publishedAddressJa !== null || station.facilityNameJa !== null || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Refusing unexpected pre-update state for ${station.code}`);
  }
  station.publishedAddressJa = update.address;
  station.facilityNameJa = update.facility;
  station.metadataStatus = 'source_verified';
  station.sourceUrls.push(update.placementSource, update.addressSource);
  station.note = update.note;
  station.placementPrecision = 'exact_address';
  updates.delete(station.code);
}

if (updates.size) throw new Error(`Missing expected stations: ${[...updates.keys()].join(', ')}`);
if (data.coverage.exactPlacementAddressUpdates !== 2130 || data.coverage.localityPlacementRecords !== 1557) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2132;
data.coverage.localityPlacementRecords = 1555;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
