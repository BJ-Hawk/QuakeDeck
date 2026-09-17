import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.city.isumi.lg.jp/material/files/group/14/dai2hen_tunami-jisintaisaku.pdf';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1223834');

if (!station) throw new Error('Missing station 1223834');
if (station.publishedAddressJa !== null || station.facilityNameJa !== null || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Refusing unexpected pre-update state for 1223834');
}
station.publishedAddressJa = '千葉県いすみ市岬町長者549';
station.facilityNameJa = 'いすみ市役所岬庁舎';
station.metadataStatus = 'source_verified';
station.sourceUrls.push(source);
station.note = 'Isumi City’s disaster plan directly lists the meter at the Misaki municipal office site, outside the building, with its Japanese address.';
station.placementPrecision = 'exact_address';

if (data.coverage.exactPlacementAddressUpdates !== 2137 || data.coverage.localityPlacementRecords !== 1550) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2138;
data.coverage.localityPlacementRecords = 1549;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
