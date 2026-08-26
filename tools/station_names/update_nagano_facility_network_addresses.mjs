import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2020332',
    facility: '真田消防署',
    address: '長野県上田市真田町長7174番地1',
    sourceUrl: 'https://www.city.ueda.nagano.jp/soshiki/shobo-honsomu/1529.html',
  },
  {
    code: '2020630',
    facility: '諏訪市役所',
    address: '長野県諏訪市高島一丁目22番30号',
    sourceUrl: 'https://www.city.suwa.lg.jp/map/39308.html',
  },
  {
    code: '2021432',
    facility: '茅野市葛井公園',
    address: '長野県茅野市ちの263番地13',
    sourceUrl: 'https://www.city.chino.lg.jp/site/kids/1452.html',
  },
  {
    code: '2021530',
    facility: '塩尻消防署',
    address: '長野県塩尻市広丘原新田575番地9',
    sourceUrl: 'https://www.m-kouiki119.jp/mrfb/shozaichi/shozokuichiran/',
    addressPublisher: 'the public fire authority',
  },
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const { code, facility, address, sourceUrl, addressPublisher = 'the municipality' } of updates) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station) throw new Error(`Station ${code} not found.`);
  if (station.facilityNameJa !== facility) {
    throw new Error(`Station ${code} does not identify ${facility} as the facility.`);
  }
  if (station.publishedAddressJa) throw new Error(`Station ${code} already has an address.`);

  station.publishedAddressJa = address;
  station.metadataStatus = 'Official prefectural placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; ${addressPublisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} stations.`);
