import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2621237', '京丹後市役所弥栄庁舎', '京都府京丹後市弥栄町溝谷3464番地', 'https://www.city.kyotango.lg.jp/top/soshiki/somu/somu/2/1950.html', 'Kyotango City'],
  ['2646530', '与謝野町役場加悦庁舎', '京都府与謝郡与謝野町字加悦433番地', 'https://www.town.yosano.lg.jp/administration/office-organization/kaya/entry_82/', 'Yosano Town'],
];

for (const [code, facility, address, sourceUrl, publisher] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) throw new Error(`${code}: expected ${facility}, got ${station.facilityNameJa}`);
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);
  station.publishedAddressJa = address;
  station.metadataStatus = 'Documented seismic-network placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's seismic-network documentation identifies ${facility} as the host site; ${publisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
