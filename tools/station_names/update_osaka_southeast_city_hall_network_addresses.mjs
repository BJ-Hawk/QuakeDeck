import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2721333', '泉佐野市役所', '大阪府泉佐野市市場東1丁目1番1号', 'https://www.city.izumisano.lg.jp/izumisano/gaiyou/index.html', 'Izumisano City'],
  ['2721630', '河内長野市役所', '大阪府河内長野市原町1丁目1-1', 'https://www.city.kawachinagano.lg.jp/map/shityosya.html', 'Kawachinagano City'],
  ['2721932', '和泉市役所', '大阪府和泉市府中町二丁目7番5号', 'https://www.city.osaka-izumi.lg.jp/index.html', 'Izumi City'],
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
