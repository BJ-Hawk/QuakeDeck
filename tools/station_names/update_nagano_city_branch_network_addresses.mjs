import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2020130',
    facility: '戸隠支所',
    address: '長野県長野市戸隠豊岡1554番地',
    sourceUrl: 'https://www.city.nagano.nagano.jp/n088475/shisetsu/p000294.html',
  },
  {
    code: '2020131',
    facility: '鬼無里支所',
    address: '長野県長野市鬼無里日影2750番地1',
    sourceUrl: 'https://www.city.nagano.nagano.jp/n088500/shisetsu/p000291.html',
  },
  {
    code: '2020134',
    facility: '豊野支所',
    address: '長野県長野市豊野町豊野630番地1',
    sourceUrl: 'https://www.city.nagano.nagano.jp/n088450/shisetsu/p000318.html',
  },
  {
    code: '2020135',
    facility: '大岡支所',
    address: '長野県長野市大岡乙287',
    sourceUrl: 'https://www.city.nagano.nagano.jp/n088525/shisetsu/p000305.html',
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
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; Nagano City publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} stations.`);
