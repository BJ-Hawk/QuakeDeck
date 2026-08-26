import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2721730', '松原市役所', '大阪府松原市阿保1丁目1番1号', 'https://www.city.matsubara.lg.jp/shisei/matsubara_gaiyo/shokai/', 'Matsubara City'],
  ['2723230', '阪南市役所', '大阪府阪南市尾崎町35番地の1', 'https://www.city.hannan.lg.jp/shisei/shisei/index.html', 'Hannan City'],
  ['2734132', '忠岡町役場', '大阪府泉北郡忠岡町忠岡東1丁目34番1号', 'https://www.town.tadaoka.osaka.jp/gyousei/gaiyou/index.html', 'Tadaoka Town'],
  ['2736230', '田尻町役場', '大阪府泉南郡田尻町嘉祥寺375番地1', 'https://www.town.tajiri.osaka.jp/johowosagasu/shisetsuannai/tajirityoyakuba/index.html', 'Tajiri Town'],
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
