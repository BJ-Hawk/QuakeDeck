import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '1621030',
    facility: '城端庁舎',
    address: '富山県南砺市城端1046番地',
    sourceUrl: 'https://www.city.nanto.toyama.jp/kurashi-tetsuzuki/yakusho-madoguchiannai/4586.html',
  },
  {
    code: '1621031',
    facility: '平行政センター',
    address: '富山県南砺市下梨2240番地',
    sourceUrl: 'https://www.city.nanto.toyama.jp/soshiki/shimin/6/1058.html',
  },
  {
    code: '1621032',
    facility: '上平行政センター',
    address: '富山県南砺市上平細島879',
    sourceUrl: 'https://www.city.nanto.toyama.jp/cms-sypher/open_imgs/info/0000037056.pdf',
  },
  {
    code: '1621034',
    facility: '井波庁舎',
    address: '富山県南砺市井波520',
    sourceUrl: 'https://www.city.nanto.toyama.jp/johoosagasu/kakuka-shisetsuichiran/1/4657.html',
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
