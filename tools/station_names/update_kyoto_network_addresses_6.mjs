import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2646331', '伊根町役場', '京都府与謝郡伊根町字日出651番地', 'https://www.town.ine.kyoto.jp/material/files/group/3/hinansyoitizu_.pdf', 'Ine Town'],
  ['2636730', '南山城村役場', '京都府相楽郡南山城村大字北大河原小字久保14番地1', 'https://www.vill.minamiyamashiro.lg.jp/cmsfiles/contents/0000002/2519/tenshutsu_yuso.pdf', 'Minamiyamashiro Village'],
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
