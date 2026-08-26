import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2721430', '富田林市消防本部金剛分署', '大阪府富田林市高辺台二丁目1番1号', 'https://www.city.tondabayashi.lg.jp/uploaded/attachment/91520.pdf', 'Tondabayashi City'],
  ['2722830', '泉州南消防組合泉南消防署', '大阪府泉南市信達市場2012番地の1', 'https://www.senshu-minami119.jp/articles/1081.html', 'Senshu Minami Fire Department'],
  ['2736130', '泉州南消防組合熊取消防署', '大阪府泉南郡熊取町野田一丁目1番19号', 'https://www.senshu-minami119.jp/articles/1081.html', 'Senshu Minami Fire Department'],
];

for (const [code, facility, address, sourceUrl, publisher] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) throw new Error(`${code}: expected ${facility}, got ${station.facilityNameJa}`);
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);
  station.publishedAddressJa = address;
  station.metadataStatus = 'Official prefectural placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; ${publisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
