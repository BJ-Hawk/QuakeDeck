import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2721831', '大東四條畷消防組合消防本部', '大阪府大東市新町13番35号', 'https://www.ds119.jp/shobo/', 'Daito-Shijonawate Fire Department'],
  ['2722930', '大東四條畷消防組合四條畷消防署', '大阪府四條畷市西中野一丁目1番26号', 'https://www.ds119.jp/shobo/', 'Daito-Shijonawate Fire Department'],
  ['2721332', '泉州南広域消防本部', '大阪府泉佐野市りんくう往来北1番地の20', 'https://www.fdma.go.jp/publication/handbook/items/20260302_binran.pdf', 'the Fire and Disaster Management Agency'],
];

for (const [code, facility, address, sourceUrl, publisher] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) throw new Error(`${code}: expected ${facility}, got ${station.facilityNameJa}`);
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);
  station.publishedAddressJa = address;
  station.metadataStatus = 'Official prefectural placement and official address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; ${publisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
