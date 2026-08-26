import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const code = '2042531';
const facility = '木祖村役場';
const address = '長野県木曽郡木祖村薮原1191-1';
const sourceUrl = 'https://www.vill.kiso.nagano.jp/kurashi_joho/bosai_bohan/phone.html?newwindow=true';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === code);

if (!station) throw new Error(`Station ${code} not found.`);
if (station.facilityNameJa !== facility) {
  throw new Error(`Station ${code} does not identify ${facility} as the facility.`);
}
if (station.publishedAddressJa) throw new Error(`Station ${code} already has an address.`);

station.publishedAddressJa = address;
station.metadataStatus = 'Official prefectural placement and municipal address';
station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; the municipality publishes the facility address.`;
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${code}.`);
