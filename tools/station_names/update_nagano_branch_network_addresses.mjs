import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2020534',
    facility: '上村自治振興センター',
    address: '長野県飯田市上村754番地2',
    sourceUrl: 'https://www.city.iida.lg.jp/soshiki/69/',
  },
  {
    code: '2020535',
    facility: '南信濃自治振興センター',
    address: '長野県飯田市南信濃和田2596番地3',
    sourceUrl: 'https://www.city.iida.lg.jp/site/kenko/inquiry-001.html',
  },
  {
    code: '2035030',
    facility: '和田支所',
    address: '長野県小県郡長和町和田2872番地',
    sourceUrl: 'https://www.town.nagawa.nagano.jp/soshiki/somu/johokoho/johokohocatv/1/3/290.html',
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
