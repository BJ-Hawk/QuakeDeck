import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '2020930',
    facility: '高遠町総合支所',
    address: '長野県伊那市高遠町西高遠810番地1',
    sourceUrl: 'https://www.inacity.jp/koho/shihoina/archive/shihoina_R06/shihouR0601.files/inacity202401.pdf',
  },
  {
    code: '2020931',
    facility: '長谷総合支所',
    address: '長野県伊那市長谷溝口1394番地',
    sourceUrl: 'https://www.inacity.jp/shisetsu/shiyakusho_shisho/hasesogoshisho.html',
  },
  {
    code: '2021930',
    facility: '東御消防署',
    address: '長野県東御市県268番地1',
    sourceUrl: 'https://www.area.ueda.nagano.jp/reiki/reiki_honbun/w080RG00000088.html',
    addressPublisher: 'the public fire authority',
    status: 'Official prefectural placement and official address',
  },
  {
    code: '2021931',
    facility: '北御牧総合支所',
    address: '長野県東御市大日向337番地',
    sourceUrl: 'https://www.city.tomi.nagano.jp/i/pb-cityhall.html',
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
