import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2048130',
    facility: '池田町役場',
    address: '長野県北安曇郡池田町大字池田3203-6',
    sourceUrl: 'https://www.ikedamachi.net/soshiki_list.html',
  },
  {
    code: '2056231',
    facility: '木島平村役場',
    address: '長野県下高井郡木島平村大字往郷914番地6',
    sourceUrl: 'https://www.kijimadaira-ldc.jp/soshiki/murayakuba/more.p3.html',
  },
  {
    code: '2058830',
    facility: '小川村役場',
    address: '長野県上水内郡小川村大字高府8800-8',
    sourceUrl: 'https://www.vill.ogawa.nagano.jp/docs/6245.html',
  },
  {
    code: '2059033',
    facility: '飯綱町役場',
    address: '長野県上水内郡飯綱町大字牟礼2795-1',
    sourceUrl: 'https://www.town.iizuna.nagano.jp/docs/666.html',
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
