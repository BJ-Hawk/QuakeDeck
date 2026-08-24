import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '1632130',
    facility: '舟橋村役場',
    address: '富山県中新川郡舟橋村仏生寺55',
    sourceUrl: 'https://www.vill.funahashi.toyama.jp/gyosei_new/',
  },
  {
    code: '1620531',
    facility: '氷見市消防本部',
    address: '富山県氷見市加納387番地1',
    sourceUrl: 'https://www.city.himi.toyama.jp/gyosei/soshiki/hishokoho/1/1_1/1144.html',
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
