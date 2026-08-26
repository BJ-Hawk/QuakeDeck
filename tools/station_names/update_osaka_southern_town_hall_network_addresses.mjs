import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2736630', '岬町役場', '大阪府泉南郡岬町深日2000-1', 'https://www.town.misaki.osaka.jp/ijyu/access/1799.html', 'Misaki Town'],
  ['2738130', '太子町役場', '大阪府南河内郡太子町大字山田88番地', 'https://www.town.taishi.osaka.jp/', 'Taishi Town'],
  ['2738230', '河南町役場', '大阪府南河内郡河南町大字白木1359番地の6', 'https://www.town.kanan.osaka.jp/faq/faq_gyosei/1/4442.html', 'Kanan Town'],
  ['2738333', '千早赤阪村役場', '大阪府南河内郡千早赤阪村大字水分180番地', 'https://www.vill.chihayaakasaka.osaka.jp/sonsei/muranitsuite/index.html', 'Chihayaakasaka Village'],
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
