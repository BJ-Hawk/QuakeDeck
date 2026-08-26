import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2020533',
    facility: '飯田市役所',
    address: '長野県飯田市大久保町2534',
    sourceUrl: 'https://www.city.iida.lg.jp/soshiki/8/cityhallguide.html',
  },
  {
    code: '2044630',
    facility: '麻績村役場',
    address: '長野県東筑摩郡麻績村麻3837番地',
    sourceUrl: 'https://www.vill.omi.nagano.jp/omimura/johokokai/index.html',
  },
  {
    code: '2045230',
    facility: '筑北村役場',
    address: '長野県東筑摩郡筑北村西条4195',
    sourceUrl: 'https://www.vill.chikuhoku.lg.jp/shisetsu/list/001/',
  },
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const { code, facility, address, sourceUrl } of updates) {
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
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} stations.`);
