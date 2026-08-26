import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2048630',
    facility: '小谷村役場',
    address: '長野県北安曇郡小谷村大字中小谷丙131',
    sourceUrl: 'https://www.vill.otari.nagano.jp/gyoseijoho/otarimuranitsuite/index.html',
  },
  {
    code: '2052130',
    facility: '坂城町役場',
    address: '長野県埴科郡坂城町大字坂城10050番地',
    sourceUrl: 'https://www.town.sakaki.nagano.jp/site/userguide/2740.html',
  },
  {
    code: '2054130',
    facility: '小布施町役場',
    address: '長野県上高井郡小布施町大字小布施1491番地2',
    sourceUrl: 'https://www.town.obuse.nagano.jp/group.html',
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
