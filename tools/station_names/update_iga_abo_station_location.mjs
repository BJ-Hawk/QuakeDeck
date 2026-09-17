import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const installationSource = 'https://www.city.iga.lg.jp/cmsfiles/contents/0000008/8710/2020001965zumen.pdf';
const addressSource = 'https://www.city.iga.lg.jp/cmsfiles/contents/0000009/9977/014-015.pdf';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '2421640');

if (!station) throw new Error('Missing station 2421640');
if (station.publishedAddressJa !== null || station.facilityNameJa !== null || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Refusing unexpected pre-update state for 2421640');
}
station.publishedAddressJa = '三重県伊賀市阿保151番地1';
station.facilityNameJa = '阿保地区市民センター（青山複合施設）';
station.metadataStatus = 'source_verified';
station.sourceUrls.push(installationSource, addressSource);
station.note = 'Iga City’s construction drawing directly identifies the intensity-meter measurement unit within Aoyama Complex’s Abo District Civic Center; the City’s opening notice publishes the facility’s Japanese address.';
station.placementPrecision = 'exact_address';

if (data.coverage.exactPlacementAddressUpdates !== 2138 || data.coverage.localityPlacementRecords !== 1549) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2139;
data.coverage.localityPlacementRecords = 1548;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
