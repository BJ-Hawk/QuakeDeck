import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.city.isumi.lg.jp/material/files/group/14/dai2hen_tunami-jisintaisaku.pdf';
const addressSourceUrl = 'https://www.city.isumi.lg.jp/access/to_municipaloffice.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '1223831');
if (!station || station.prefectureJa !== '千葉県' || station.nameJa !== 'いすみ市大原') {
  throw new Error('Unexpected station for 1223831');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 1223831 already has placement metadata');
}

station.facilityNameJa = 'いすみ市役所大原庁舎';
station.publishedAddressJa = '千葉県いすみ市大原7400-1';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Isumi City disaster plan and City Hall record';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, addressSourceUrl])];
station.note = 'Isumi City\'s disaster plan explicitly identifies the intensity meter at the Oohara City Hall building and gives this address; the current City Hall record confirms the same facility and address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 1223831 Isumi City Hall Oohara Building.');
