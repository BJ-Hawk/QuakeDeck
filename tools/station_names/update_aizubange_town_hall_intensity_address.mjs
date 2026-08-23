import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.town.aizubange.fukushima.jp/uploaded/attachment/2336.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '0742130');

if (!station) throw new Error('Station 0742130 not found.');

if (!station.publishedAddressJa && !station.facilityNameJa) {
  station.facilityNameJa = '会津坂下町役場';
  station.publishedAddressJa = '福島県河沼郡会津坂下町字市中三番甲3662番地';
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Aizubange Town identifies this Fukushima Prefecture seismic-network meter as installed on the Town Hall grounds and publishes its full address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  console.log('Updated 0742130.');
} else {
  console.log('0742130 already has placement data; no change made.');
}
