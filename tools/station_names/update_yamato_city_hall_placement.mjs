import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.city.yamato.lg.jp/material/files/group/69/09_tuushinn.pdf';
const addressSourceUrl = 'https://www.city.yamato.lg.jp/gyosei/soshik/26/shinoshokai/chosha_shisetsuannai/7200.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '1421332');
if (!station || station.prefectureJa !== '神奈川県' || station.nameJa !== '大和市下鶴間' || station.municipalityStationCount !== 1) {
  throw new Error('Unexpected station for 1421332');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 1421332 already has placement metadata');
}

station.facilityNameJa = '大和市役所本庁舎';
station.publishedAddressJa = '神奈川県大和市下鶴間1-1-1';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Yamato City seismic-intensity and City Hall records';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, addressSourceUrl])];
station.note = 'Yamato City’s official fire annual report identifies the city seismic intensity meter as installed at City Hall; this is the sole catalogue station in the municipality and its published label is Yamato City Shimo-Tsuruma.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 1421332 Yamato City Shimo-Tsuruma.');
