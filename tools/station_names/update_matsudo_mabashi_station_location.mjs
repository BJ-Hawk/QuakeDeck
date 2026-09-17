import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.matsudo.chiba.jp/kurashi/anzen_anshin/sonae/shinndokei.html';
const addressSource = 'https://www.city.matsudo.chiba.jp/matfd/shoukai/syoubousyo_itiran/mabashi.html';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1220740');

if (!station) throw new Error('Missing station 1220740');
if (station.publishedAddressJa !== null || station.facilityNameJa !== null || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Refusing unexpected pre-update state for 1220740');
}
station.publishedAddressJa = '千葉県松戸市西馬橋蔵元町179番地';
station.facilityNameJa = '馬橋消防署';
station.metadataStatus = 'source_verified';
station.sourceUrls.push(placementSource, addressSource);
station.note = 'Matsudo City directly states that the intensity meter published as Matsudo City Nishimabashi is installed inside Mabashi Fire Station; the Fire Bureau publishes the station’s Japanese address.';
station.placementPrecision = 'exact_address';

if (data.coverage.exactPlacementAddressUpdates !== 2136 || data.coverage.localityPlacementRecords !== 1551) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2137;
data.coverage.localityPlacementRecords = 1550;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
