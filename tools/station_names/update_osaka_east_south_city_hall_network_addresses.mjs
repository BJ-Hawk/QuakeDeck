import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2722731', '東大阪市役所', '大阪府東大阪市荒本北一丁目1番1号', 'https://www.city.higashiosaka.lg.jp/0000005557.html', 'Higashiosaka City'],
  ['2723030', '交野市役所', '大阪府交野市私部1丁目1番1号', 'https://www.city.katano.osaka.jp/docs/2011072700017/', 'Katano City'],
  ['2720631', '泉大津市役所', '大阪府泉大津市東雲町9番12号', 'https://www.city.izumiotsu.lg.jp/shisei/sinogaiyou/index.html', 'Izumiotsu City'],
  ['2720831', '貝塚市役所', '大阪府貝塚市畠中1丁目17番1号', 'https://www.city.kaizuka.lg.jp/kakuka/soumu/shomu/topics/access_map.html', 'Kaizuka City'],
];

for (const [code, facility, address, sourceUrl, publisher] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) throw new Error(`${code}: expected ${facility}, found ${station.facilityNameJa}`);
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);
  station.publishedAddressJa = address;
  station.metadataStatus = 'Official prefectural placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; ${publisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
