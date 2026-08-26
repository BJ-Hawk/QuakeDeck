import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  {
    code: '2040730',
    facility: '阿智村コミュニティ館',
    address: '長野県下伊那郡阿智村駒場483番地',
    sourceUrl: 'https://www.vill.achi.lg.jp/soshiki/2/2009-11-post-81.html',
  },
  {
    code: '2040731',
    facility: '浪合振興室',
    address: '長野県下伊那郡阿智村浪合1018番地',
    sourceUrl: 'https://www.vill.achi.lg.jp/soshiki/2/2026-post-80.html',
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
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${update.facility} as the host site; Achi Village publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), update.sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
