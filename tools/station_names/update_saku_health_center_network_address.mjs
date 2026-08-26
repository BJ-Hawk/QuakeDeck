import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const code = '2021733';
const facility = '佐久市保健センター';
const address = '長野県佐久市中込3045-1';
const sourceUrl = 'https://www.city.saku.nagano.jp/kenko/kenshin_yobosesshu/yobosesshu/yobousessyu-rireki.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === code);

if (!station) throw new Error(`Missing station ${code}`);
if (station.facilityNameJa !== facility) {
  throw new Error(`${code}: expected ${facility}, found ${station.facilityNameJa}`);
}
if (station.publishedAddressJa) throw new Error(`${code}: address already present`);

station.publishedAddressJa = address;
station.metadataStatus = 'Official prefectural placement and municipal address';
station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; Saku City publishes the facility address.`;
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
