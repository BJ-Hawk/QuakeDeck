import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2020231',
    facility: '四賀支所',
    address: '長野県松本市会田1001番地1',
  },
  {
    code: '2020233',
    facility: '梓川支所',
    address: '長野県松本市梓川梓2288番地3',
  },
  {
    code: '2020234',
    facility: '松本市役所',
    address: '長野県松本市丸の内3番7号',
  },
  {
    code: '2020235',
    facility: '波田支所',
    address: '長野県松本市波田4417番地1',
  },
  {
    code: '2020236',
    facility: '奈川支所',
    address: '長野県松本市奈川3301番地',
  },
];
const sourceUrl = 'https://www.city.matsumoto.nagano.jp/soshiki/49/4199.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const { code, facility, address } of updates) {
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
