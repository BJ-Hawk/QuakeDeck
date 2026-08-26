import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2621432', '木津川市役所加茂支所', '京都府木津川市加茂町里南古田156番地', 'https://www.city.kizugawa.lg.jp/0000001113.html'],
  ['2621433', '木津川市役所', '京都府木津川市木津南垣外110番地9', 'https://www.city.kizugawa.lg.jp/0000001113.html'],
  ['2621434', '木津川市役所山城支所', '京都府木津川市山城町上狛北的場3番地1', 'https://www.city.kizugawa.lg.jp/0000001113.html'],
];

for (const [code, facility, address, sourceUrl] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) throw new Error(`${code}: expected ${facility}, got ${station.facilityNameJa}`);
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);
  station.publishedAddressJa = address;
  station.metadataStatus = 'Documented seismic-network placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's seismic-network documentation identifies ${facility} as the host site; Kizugawa City publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
