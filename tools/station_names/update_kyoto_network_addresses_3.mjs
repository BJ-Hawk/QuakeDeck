import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2646531', '与謝野町役場', '京都府与謝郡与謝野町字岩滝1798番地1', 'https://www.town.yosano.lg.jp/reiki/reiki_honbun/r323RG00000001.html', 'Yosano Town'],
  ['2646532', '与謝野町役場野田川庁舎', '京都府与謝郡与謝野町字四辻65番地', 'https://www.town.yosano.lg.jp/reiki/reiki_honbun/r323RG00000001.html', 'Yosano Town'],
  ['2610830', '京都市右京区役所京北出張所', '京都府京都市右京区京北周山町上寺田1番地1', 'https://www.city.kyoto.lg.jp/bunshi/page/0000003123.html', 'Kyoto City'],
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
