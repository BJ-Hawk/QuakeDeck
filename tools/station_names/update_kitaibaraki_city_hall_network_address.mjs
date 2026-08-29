import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '0821530');

if (!station) throw new Error('Station 0821530 was not found.');
if (station.publishedAddressJa || station.facilityNameJa) {
  throw new Error('Station 0821530 already has placement metadata.');
}

station.facilityNameJa = '北茨城市役所';
station.publishedAddressJa = '茨城県北茨城市磯原町磯原1630';
station.metadataStatus = 'Official Kitaibaraki City seismic-meter placement confirmation';
station.note = 'Kitaibaraki City directly states that its seismic-intensity meter is installed on City Hall grounds and publishes the hall address.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.city.kitaibaraki.lg.jp/faq/docs/2015040200080/'])];
delete station.placementLocalityJa;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
