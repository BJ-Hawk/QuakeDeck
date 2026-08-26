import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2720530', '吹田市消防本部南消防署', '大阪府吹田市内本町1丁目23番14号', 'https://www.city.suita.osaka.jp/shisei/shisetsu/1019109/1020361.html', 'Suita City'],
  ['2721130', '茨木市消防本部', '大阪府茨木市東中条町2番13号', 'https://www.city.ibaraki.osaka.jp/office/hobun/reiki_int/reiki_honbun/k213RG00000538.html', 'Ibaraki City'],
  ['2722031', '箕面市消防本部東分署', '大阪府箕面市粟生外院2-4-7', 'https://www.city.minoh.lg.jp/shisetsu/yobou/shouboushohigashi.html', 'Minoh City'],
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
