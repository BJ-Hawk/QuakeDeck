import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2022033',
    facility: '堀金支所',
    address: '長野県安曇野市堀金烏川2750番地1',
  },
  {
    code: '2022034',
    facility: '明科支所',
    address: '長野県安曇野市明科中川手6824番地1',
  },
  {
    code: '2022035',
    facility: '豊科交流学習センター',
    address: '長野県安曇野市豊科5609番地3',
  },
  {
    code: '2022036',
    facility: '三郷支所',
    address: '長野県安曇野市三郷明盛4810番地1',
  },
];
const sourceUrl = 'https://www.city.azumino.nagano.jp/uploaded/attachment/80973.pdf';
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
