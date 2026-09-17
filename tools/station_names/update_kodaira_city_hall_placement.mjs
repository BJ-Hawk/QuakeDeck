import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.kodaira.tokyo.jp/kurashi/files/99726/099726/att_0000003.pdf';
const facilitySource = 'https://www.city.kodaira.tokyo.jp/kurashi/004/004034.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1321131');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '小平市小川町' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for Kodaira City Hall placement.');
}

station.facilityNameJa = '小平市役所';
station.publishedAddressJa = '東京都小平市小川町2丁目1333番地';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSource, facilitySource])];
station.note = 'Kodaira City’s current earthquake continuity plan places its measuring-intensity meter in Room 301 of the main City Hall building. The active public station label is Kodaira City Ogawacho, matching the City Hall locality; the city publishes the facility address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
