import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2021735',
    facility: '望月支所',
    address: '長野県佐久市望月263',
    sourceUrl: 'https://www.city.saku.nagano.jp/soshiki/10/80040/index.html',
  },
  {
    code: '2021736',
    facility: '臼田総合福祉センター',
    address: '長野県佐久市下越16番地5',
    sourceUrl: 'https://www.city.saku.nagano.jp/kenko/ikiiki/shisetsu/20200526.html',
  },
  {
    code: '2021737',
    facility: '浅科支所',
    address: '長野県佐久市甲1359番地3',
    sourceUrl: 'https://www.city.saku.nagano.jp/soshiki/10/80030/index.html',
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
