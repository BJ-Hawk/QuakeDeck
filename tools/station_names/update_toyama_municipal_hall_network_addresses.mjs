import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '1620630',
    facility: '滑川市役所',
    address: '富山県滑川市寺家町104',
    sourceUrl: 'https://www.city.namerikawa.toyama.jp/soshiki/4/2/1/711.html',
  },
  {
    code: '1634231',
    facility: '入善町役場',
    address: '富山県下新川郡入善町入膳423',
    sourceUrl: 'https://www.town.nyuzen.toyama.jp/gyosei/chosei/yakuba/index.html',
  },
  {
    code: '1620830',
    facility: '砺波市役所',
    address: '富山県砺波市栄町7番3号',
    sourceUrl: 'https://www.city.tonami.lg.jp/info/5643p/',
  },
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const { code, facility, address, sourceUrl } of updates) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station) throw new Error(`Station ${code} not found.`);
  if (station.facilityNameJa !== facility) {
    throw new Error(`Station ${code} does not identify ${facility} as its facility.`);
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
