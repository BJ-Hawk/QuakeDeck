import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2621230', '京丹後市役所', '京都府京丹後市峰山町杉谷889番地', 'https://www.city.kyotango.lg.jp/top/shisei/gaiyo/4/3920.html'],
  ['2621231', '京丹後市役所大宮庁舎', '京都府京丹後市大宮町口大野226番地', 'https://www.city.kyotango.lg.jp/top/soshiki/jogesuido/shisetsukanri/gesui/2/22309.html'],
  ['2621233', '京丹後市役所丹後庁舎', '京都府京丹後市丹後町間人1780番地', 'https://www.city.kyotango.lg.jp/top/soshiki/jogesuido/suido/2/21755.html'],
  ['2621235', '京丹後市役所久美浜庁舎', '京都府京丹後市久美浜町814番地', 'https://www.city.kyotango.lg.jp/top/shisei/gaiyo/4/6115.html'],
];

for (const [code, facility, address, sourceUrl] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) throw new Error(`${code}: expected ${facility}, got ${station.facilityNameJa}`);
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);
  station.publishedAddressJa = address;
  station.metadataStatus = 'Documented seismic-network placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's seismic-network documentation identifies ${facility} as the host site; Kyotango City publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
