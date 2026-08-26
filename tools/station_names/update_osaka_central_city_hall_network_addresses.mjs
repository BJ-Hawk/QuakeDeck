import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2721230', '八尾市役所', '大阪府八尾市本町一丁目1番1号', 'https://www.city.yao.osaka.jp/shisei/yaoshinoshoukai/1009258/1012669.html', 'Yao City'],
  ['2722131', '柏原市役所', '大阪府柏原市安堂町1番55号', 'https://www.city.kashiwara.lg.jp/docs/2014062700202/', 'Kashiwara City'],
  ['2722331', '門真市役所', '大阪府門真市中町1-1', 'https://www.city.kadoma.osaka.jp/shisei/shisetsushokai/3939.html', 'Kadoma City'],
  ['2722430', '摂津市役所', '大阪府摂津市三島1丁目1番1号', 'https://www.city.settsu.osaka.jp/map/shiyakusho/index.html', 'Settsu City'],
];

for (const [code, facility, address, sourceUrl, publisher] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) {
    throw new Error(`${code}: expected ${facility}, found ${station.facilityNameJa}`);
  }
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);

  station.publishedAddressJa = address;
  station.metadataStatus = 'Official prefectural placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; ${publisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
