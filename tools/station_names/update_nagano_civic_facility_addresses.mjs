import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  {
    code: '2020240',
    facility: '松本市寿公民館',
    address: '長野県松本市寿豊丘424番地',
    sourceUrl: 'https://www.city.matsumoto.nagano.jp/soshiki/114/4290.html',
    publisher: 'Matsumoto City',
  },
  {
    code: '2021831',
    facility: '旧上山田庁舎',
    address: '長野県千曲市上山田温泉四丁目15-1',
    sourceUrl: 'https://www.city.chikuma.lg.jp/gyoseijoho/yakusho_madoguchiannai/3136.html',
    publisher: 'Chikuma City',
  },
  {
    code: '2021832',
    facility: '旧戸倉庁舎',
    address: '長野県千曲市大字戸倉2388番地',
    sourceUrl: 'https://www.city.chikuma.lg.jp/soshiki/koreifukushi/shogaishafukushi/1/1185.html',
    publisher: 'Chikuma City',
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
  station.metadataStatus = 'Official prefectural placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${update.facility} as the host site; ${update.publisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), update.sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
