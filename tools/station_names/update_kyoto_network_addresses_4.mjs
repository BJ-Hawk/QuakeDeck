import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2620731', '城陽市役所', '京都府城陽市寺田東ノ口16番地・17番地', 'https://www.city.joyo.kyoto.jp/0000001690.html', 'Joyo City'],
  ['2621030', '八幡市役所分庁舎', '京都府八幡市八幡高畑1番地の1', 'https://www.city.yawata.kyoto.jp/0000000673.html', 'Yawata City'],
  ['2621130', '京田辺市消防本部', '京都府京田辺市田辺鳥本102番地', 'https://www.city.kyotanabe.lg.jp/cmsfiles/contents/0000013/13351/230309-08.pdf', 'Kyotanabe City'],
];

for (const [code, facility, address, sourceUrl, publisher] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) throw new Error(`${code}: expected ${facility}, got ${station.facilityNameJa}`);
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);
  station.publishedAddressJa = address;
  station.metadataStatus = 'Documented seismic-network placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's seismic-network documentation identifies ${facility} as the host site; ${publisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
