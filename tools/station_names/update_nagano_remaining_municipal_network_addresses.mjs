import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2030630',
    facility: '南相木村役場',
    address: '長野県南佐久郡南相木村3525番地1',
    sourceUrl: 'https://www.minamiaiki.jp/maiki-cms/wp-content/uploads/2025/05/b39e434b0bb55708809efe8b4bf95de3.pdf',
  },
  {
    code: '2040230',
    facility: '松川町役場',
    address: '長野県下伊那郡松川町元大島3823',
    sourceUrl: 'https://www.town.matsukawa.lg.jp/choseijoho/matsukawamachinoshokai/matsukawamachiyakuba_soshiki_toiawase/index.html',
  },
  {
    code: '2040430',
    facility: '阿南町役場',
    address: '長野県下伊那郡阿南町東條58番地1',
    sourceUrl: 'https://www.pref.nagano.lg.jp/shichoson/kensei/shichoson/gappei/gappei/mejiiko/ichiran/',
    addressPublisher: 'Nagano Prefecture',
    status: 'Official prefectural placement and official address',
  },
  {
    code: '2041330',
    facility: '天龍村役場',
    address: '長野県下伊那郡天龍村平岡878番地',
    sourceUrl: 'https://www.vill-tenryu.jp/administrative/government_info/about/info-tenryu-village/sosiki/',
  },
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const { code, facility, address, sourceUrl, addressPublisher = 'the municipality', status = 'Official prefectural placement and municipal address' } of updates) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station) throw new Error(`Station ${code} not found.`);
  if (station.facilityNameJa !== facility) {
    throw new Error(`Station ${code} does not identify ${facility} as the facility.`);
  }
  if (station.publishedAddressJa) throw new Error(`Station ${code} already has an address.`);

  station.publishedAddressJa = address;
  station.metadataStatus = status;
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; ${addressPublisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} stations.`);
