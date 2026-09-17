import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const facilityAddressSource = 'https://www.pref.kagoshima.jp/aj01/documents/105080_20240402112946-1.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '4652531');

if (
  !station ||
  station.prefectureJa !== '鹿児島県' ||
  station.nameJa !== '瀬戸内町請島' ||
  station.facilityNameJa !== '瀬戸内町請島池地集会所' ||
  station.publishedAddressJa ||
  station.placementPrecision !== 'municipality_or_ward'
) {
  throw new Error('Unexpected starting state for 4652531.');
}

station.publishedAddressJa = '鹿児島県大島郡瀬戸内町池地638番地';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official prefectural seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([...station.sourceUrls, facilityAddressSource])];
station.note = 'Kagoshima Prefecture’s seismic-meter maintenance table identifies the host as Setouchi Town Ukejima Ikejichi Assembly Hall. Kagoshima Prefecture’s current designated-evacuation-facility list publishes the address of Ikejichi Assembly Hall.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
