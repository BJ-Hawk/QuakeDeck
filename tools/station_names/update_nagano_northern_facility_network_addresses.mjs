import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2020730',
    facility: '須坂市役所',
    address: '長野県須坂市大字須坂1528番地1',
    sourceUrl: 'https://www.city.suzaka.nagano.jp/soshiki/1010/1/498.html',
  },
  {
    code: '2021240',
    facility: 'ぽかぽかランド美麻',
    address: '長野県大町市美麻16784',
    sourceUrl: 'https://www.city.omachi.nagano.jp/jigyosha/brand/damcurry/rwinyxnf51g9e5n6aarx2upd',
  },
  {
    code: '2021834',
    facility: '千曲市役所',
    address: '長野県千曲市杭瀬下二丁目1番地',
    sourceUrl: 'https://www.city.chikuma.lg.jp/gyoseijoho/yakusho_madoguchiannai/index.html',
  },
  {
    code: '2059032',
    facility: 'りんごパークセンター',
    address: '長野県上水内郡飯綱町大字芋川161',
    sourceUrl: 'https://www.town.iizuna.nagano.jp/docs/336.html',
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
