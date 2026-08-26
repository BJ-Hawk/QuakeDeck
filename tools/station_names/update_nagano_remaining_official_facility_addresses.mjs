import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  {
    code: '2020431',
    facility: '岡谷市保健センター',
    address: '長野県岡谷市幸町8番1号',
    sourceUrl: 'https://www1.g-reiki.net/okaya/reiki_honbun/e705RG00000445.html',
    addressPublisher: 'Okaya City ordinance',
    status: 'Official prefectural placement and municipal address',
  },
  {
    code: '2041630',
    facility: '豊丘村役場',
    address: '長野県下伊那郡豊丘村大字神稲3120',
    sourceUrl: 'https://www.vill.nagano-toyooka.lg.jp/99etc/contact.html',
    addressPublisher: 'Toyooka Village',
    status: 'Official prefectural placement and municipal address',
  },
  {
    code: '2043235',
    facility: '日義支所',
    address: '長野県木曽郡木曽町日義1602番地',
    sourceUrl: 'https://www.pref.nagano.lg.jp/kokai/kensei/kenpo/h21/h21-04/documents/20090423_1.pdf',
    addressPublisher: 'a Nagano Prefecture publication',
    status: 'Official prefectural placement and official address',
  },
  {
    code: '2045231',
    facility: '筑北村坂北支所',
    address: '長野県東筑摩郡筑北村坂北2187番地',
    sourceUrl: 'https://www.vill.chikuhoku.lg.jp/fs/5/6/7/_/2ff3111af15700d25d0bddf9d6f242cf.pdf',
    addressPublisher: 'Chikuhoku Village',
    status: 'Official prefectural placement and municipal address',
  },
  {
    code: '2060240',
    facility: '秋山郷総合センター',
    address: '長野県下水内郡栄村大字堺18281',
    sourceUrl: 'https://db.go-nagano.net/topics_detail6/id%3D12055',
    addressPublisher: "Nagano Prefecture's official tourism site",
    status: 'Official prefectural placement and official address',
  },
];

for (const update of updates) {
  const station = data.stations.find((candidate) => candidate.code === update.code);
  if (!station) throw new Error(`Missing station ${update.code}`);
  if (station.facilityNameJa !== update.facility) {
    throw new Error(`${update.code}: expected ${update.facility}, found ${station.facilityNameJa}`);
  }
  if (station.publishedAddressJa) throw new Error(`${update.code}: address already present`);

  station.publishedAddressJa = update.address;
  station.metadataStatus = update.status;
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${update.facility} as the host site; ${update.addressPublisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), update.sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
