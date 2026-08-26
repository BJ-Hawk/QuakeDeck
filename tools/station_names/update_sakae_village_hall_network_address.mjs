import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const code = '2060230';
const facility = '栄村役場';
const address = '長野県下水内郡栄村大字北信3433番地';
const sourceUrl = 'https://www.pref.nagano.lg.jp/sansei/documents/sakae.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === code);

if (!station) throw new Error(`Station ${code} not found.`);
if (station.facilityNameJa !== facility) {
  throw new Error(`Station ${code} does not identify ${facility} as the facility.`);
}
if (station.publishedAddressJa) throw new Error(`Station ${code} already has an address.`);

station.publishedAddressJa = address;
station.metadataStatus = 'Official prefectural placement and official address';
station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; a Nagano Prefecture publication gives the facility address.`;
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${code}.`);
