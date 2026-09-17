import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.pref.shiga.lg.jp/file/attachment/31987.pdf';
const activeStationSourceUrl = 'https://www.data.jma.go.jp/hikone/seismo/seismo.html';
const addressSourceUrl = 'https://www.city.kusatsu.shiga.jp/shisei/sisetsuannai/shiyakusho/access.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '2520631');
if (!station || station.prefectureJa !== '滋賀県' || station.nameJa !== '草津市草津') {
  throw new Error('Unexpected station for 2520631');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 2520631 already has placement metadata');
}

station.facilityNameJa = '草津市役所';
station.publishedAddressJa = '滋賀県草津市草津三丁目13番30号';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Shiga Prefecture network inventory and Kusatsu City Hall record';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, activeStationSourceUrl, addressSourceUrl])];
station.note = 'Shiga Prefecture explicitly maps this station name to Kusatsu City Hall; the JMA current list confirms the station remains active, and Kusatsu City publishes the City Hall address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 2520631 Kusatsu City Hall.');
