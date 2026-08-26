import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2712831', '大阪府庁', '大阪市中央区大手前2丁目1番22号', 'https://www.pref.osaka.lg.jp/o070060/johokokai/archives/access.html', 'Osaka Prefecture', 'Official prefectural placement and official address'],
  ['2730130', '島本町消防本部', '大阪府三島郡島本町若山台一丁目2番5号', 'https://www.town.shimamoto.lg.jp/site/shimamtosyoubou/', 'Shimamoto Town', 'Official prefectural placement and municipal address'],
  ['2732130', '豊能町役場', '大阪府豊能郡豊能町余野414番地の1', 'https://www.town.toyono.osaka.jp/inq.php', 'Toyono Town', 'Official prefectural placement and municipal address'],
  ['2732231', '能勢町役場', '大阪府豊能郡能勢町宿野28番地', 'https://www.town.nose.osaka.jp/soshiki/midorikankyou/midorisinko/katudoousienkyuuhuhojyo/10578.html', 'Nose Town', 'Official prefectural placement and municipal address'],
];

for (const [code, facility, address, sourceUrl, publisher, metadataStatus] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) throw new Error(`${code}: expected ${facility}, got ${station.facilityNameJa}`);
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);
  station.publishedAddressJa = address;
  station.metadataStatus = metadataStatus;
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; ${publisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
