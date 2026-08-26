import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2030430',
    facility: '川上村役場',
    address: '長野県南佐久郡川上村大字大深山525',
    sourceUrl: 'https://www.vill.kawakami.nagano.jp/www/genre/1000200000139/index.html',
  },
  {
    code: '2030530',
    facility: '南牧村役場',
    address: '長野県南佐久郡南牧村大字海ノ口1051番地',
    sourceUrl: 'https://www.minamimakimura.jp/main/about/access.html',
  },
  {
    code: '2030932',
    facility: '佐久穂町役場',
    address: '長野県南佐久郡佐久穂町大字高野町569番地',
    sourceUrl: 'https://www.town.sakuho.nagano.jp/soshiki/sogoseisaku/',
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
