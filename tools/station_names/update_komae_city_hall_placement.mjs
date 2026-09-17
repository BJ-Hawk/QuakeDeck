import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.komae.tokyo.jp/index.cfm/50%2C136838%2Cc%2Chtml/136838/20250206-085418.pdf';
const facilitySource = 'https://www.city.komae.tokyo.jp/index.cfm/41%2C0%2C324%2C2026%2Chtml';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1321931');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '狛江市和泉本町' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for Komae City Hall placement.');
}

station.facilityNameJa = '狛江市役所';
station.publishedAddressJa = '東京都狛江市和泉本町1丁目1番5号';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSource, facilitySource])];
station.note = 'Komae City’s current disaster plan states that its measuring-intensity system is installed within the City Hall grounds. The active public station label is Komae City Izumihoncho, matching the City Hall locality; the city publishes the facility address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
