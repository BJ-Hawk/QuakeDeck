import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2032130',
    facility: '軽井沢町役場',
    address: '長野県北佐久郡軽井沢町大字長倉2381番地1',
    sourceUrl: 'https://www.town.karuizawa.lg.jp/map/m6722.html',
  },
  {
    code: '2032432',
    facility: '立科町役場',
    address: '長野県北佐久郡立科町大字芦田2532',
    sourceUrl: 'https://www.town.tateshina.nagano.jp/gyoseijoho/yakusho_madoguchi/yakuba_annai/index.html',
  },
  {
    code: '2034930',
    facility: '青木村役場',
    address: '長野県小県郡青木村大字田沢111',
    sourceUrl: 'https://www.vill.aoki.nagano.jp/',
  },
  {
    code: '2036230',
    facility: '富士見町役場',
    address: '長野県諏訪郡富士見町落合10777番地',
    sourceUrl: 'https://www.town.fujimi.lg.jp/page/accsess.html',
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
