import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2021131',
    facility: '豊田支所',
    address: '長野県中野市大字豊津2508番地',
    sourceUrl: 'https://www.city.nakano.nagano.jp/soshiki/soumu/seisakujoho/',
  },
  {
    code: '2021132',
    facility: '中野市役所',
    address: '長野県中野市三好町一丁目3番19号',
    sourceUrl: 'https://www.city.nakano.nagano.jp/docs/2014011500334/',
  },
  {
    code: '2021230',
    facility: '八坂支所',
    address: '長野県大町市八坂1108番地1',
    sourceUrl: 'https://www.city.omachi.nagano.jp/00005000/doc/00005200/19-6.pdf',
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
