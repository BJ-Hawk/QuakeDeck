import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '1620138',
    facility: '山田公民館',
    address: '富山県富山市山田湯880番地',
    sourceUrl: 'https://www.city.toyama.lg.jp/kurashi/jichi/1010382/1011071/1007438.html?itemtype=1&laytype=1&print=1&temptype=1',
  },
  {
    code: '1620832',
    facility: '庄川支所',
    address: '富山県砺波市庄川町青島401',
    sourceUrl: 'https://www.city.tonami.lg.jp/info/48130p/',
  },
  {
    code: '1621036',
    facility: '福野庁舎',
    address: '富山県南砺市苗島4880',
    sourceUrl: 'https://www.city.nanto.toyama.jp/cms-sypher/open_imgs/info/0000037056.pdf',
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
