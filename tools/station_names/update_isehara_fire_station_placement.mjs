import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.city.isehara.kanagawa.jp/docs/2013050100031/file_contents/tiikibousaikeikaku.pdf';
const addressSourceUrl = 'https://www.city.isehara.kanagawa.jp/soshiki/shobo/shobosho/';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '1421432');
if (!station || station.prefectureJa !== '神奈川県' || station.nameJa !== '伊勢原市伊勢原' || station.municipalityStationCount !== 1) {
  throw new Error('Unexpected station for 1421432');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 1421432 already has placement metadata');
}

station.facilityNameJa = '伊勢原市消防署本署';
station.publishedAddressJa = '神奈川県伊勢原市伊勢原3丁目32番20号';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Isehara City seismic-observation and Fire Station records';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, addressSourceUrl])];
station.note = 'Isehara City’s disaster plan explicitly identifies the Isehara intensity observation point as the Fire Station and states that its readings are sent to JMA; this is the sole catalogue station in the municipality.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 1421432 Isehara City Isehara.');
