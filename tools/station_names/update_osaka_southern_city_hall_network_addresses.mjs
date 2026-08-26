import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2722231', '羽曳野市役所', '大阪府羽曳野市誉田4丁目1番1号', 'https://www.city.habikino.lg.jp/kurashi/shisei/habikinoshi/habikinoshinogaiyo/6242.html', 'Habikino City'],
  ['2722530', '高石市役所', '大阪府高石市加茂4丁目1番1号', 'https://www.city.takaishi.lg.jp/about/access.html', 'Takaishi City'],
  ['2722630', '藤井寺市役所', '大阪府藤井寺市岡1丁目1番1号', 'https://www.city.fujiidera.lg.jp/soshiki/somubu/soumu/osirase/1387413545768.html', 'Fujiidera City'],
  ['2723130', '大阪狭山市役所', '大阪府大阪狭山市狭山一丁目2384番地の1', 'https://www.city.osakasayama.osaka.jp/machizukuri_shisei/gaiyo/1/3764.html', 'Osakasayama City'],
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
