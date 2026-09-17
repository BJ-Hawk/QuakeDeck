import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://saigai.aij.or.jp/saigai_info/20220316_fukushima_oki/20220316_fukushima_oki_chousasokuho_TOHOKU_B.pdf';
const addressSource = 'https://www.city.soma.fukushima.jp/shiseijoho/shinogaiyo/shiyakusyo/2848.html';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '0720932');

if (!station) throw new Error('Missing station 0720932');
if (station.publishedAddressJa !== null || station.facilityNameJa !== null || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Refusing unexpected pre-update state for 0720932');
}
if (data.coverage.exactPlacementAddressUpdates !== 2129 || data.coverage.localityPlacementRecords !== 1558) {
  throw new Error('Refusing unexpected coverage state');
}

station.publishedAddressJa = '福島県相馬市中村字北町63-3';
station.facilityNameJa = '相馬市役所';
station.metadataStatus = 'source_verified';
station.sourceUrls.push(placementSource, addressSource);
station.note = 'The Architectural Institute of Japan’s 2022 field-survey report directly places the Soma City Nakamura intensity meter in a corner of Soma City Hall’s car park; Soma City publishes the hall’s Japanese address.';
station.placementPrecision = 'exact_address';
data.coverage.exactPlacementAddressUpdates = 2130;
data.coverage.localityPlacementRecords = 1557;

fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
