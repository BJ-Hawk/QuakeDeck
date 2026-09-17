import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.city.mizunami.lg.jp/_res/projects/default_project/_page_/001/003/618/h281215.pdf';
const addressSourceUrl = 'https://www.city.mizunami.lg.jp/shisetsuannai/shiyakusho_shi/1003792.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '2120831');
if (!station || station.prefectureJa !== '岐阜県' || station.nameJa !== '瑞浪市上平町' || station.municipalityStationCount !== 1) {
  throw new Error('Unexpected station for 2120831');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 2120831 already has placement metadata');
}

station.facilityNameJa = '瑞浪市役所';
station.publishedAddressJa = '岐阜県瑞浪市上平町1-1';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Mizunami City seismic-intensity and City Hall records';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, addressSourceUrl])];
station.note = 'Mizunami City states that the Gifu Prefecture seismometer is installed at City Hall; this is the sole catalogue station in the municipality and its published label is Mizunami City Kamihiracho.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 2120831 Mizunami City Kamihiracho.');
