import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2720330', '豊中市役所', '大阪府豊中市中桜塚3丁目1番1号', 'https://www.city.toyonaka.osaka.jp/shisetsu/shi/index.html', 'Toyonaka City'],
  ['2720430', '池田市役所', '大阪府池田市城南1-1-1', 'https://www.city.ikeda.osaka.jp/', 'Ikeda City'],
  ['2720931', '守口市役所', '守口市京阪本通2丁目5番5号', 'https://www.city.moriguchi.osaka.jp/gyoseijoho/shiyakusho/access.html', 'Moriguchi City'],
  ['2721031', '枚方市役所', '大阪府枚方市大垣内町2丁目1番20号', 'https://www.city.hirakata.osaka.jp/category/6-16-1-0-0-0-0-0-0-0.html', 'Hirakata City'],
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
