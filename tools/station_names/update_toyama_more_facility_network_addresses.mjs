import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '1620232',
    facility: '高岡市役所',
    address: '富山県高岡市広小路7番50号',
    sourceUrl: 'https://www.city.takaoka.toyama.jp/soshiki/kanzaikeiyakuka/2/4/3321.html',
  },
  {
    code: '1632231',
    facility: '上市町消防署',
    address: '富山県中新川郡上市町稗田36',
    sourceUrl: 'https://www.town.kamiichi.toyama.jp/map/3802.html',
  },
  {
    code: '1620732',
    facility: '黒部消防署',
    address: '富山県黒部市植木761番地1',
    sourceUrl: 'https://www.city.kurobe.toyama.jp/attach/EDIT/003/003317.pdf',
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
