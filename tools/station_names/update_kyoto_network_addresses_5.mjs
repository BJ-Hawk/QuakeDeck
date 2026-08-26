import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  ['2632230', '久御山町消防本部', '京都府久世郡久御山町島田ミスノ38番地', 'https://www.town.kumiyama.lg.jp/soshiki/8-1-0-0-0_4.html', 'Kumiyama Town'],
  ['2634331', '井手町役場', '京都府綴喜郡井手町大字井手小字東高月8番地', 'https://www.town.ide.kyoto.jp/soshiki/kyouikuiinkai/syakaikyouiku/syakai_k_news/4312.html', 'Ide Town'],
  ['2636430', '笠置町役場', '京都府相楽郡笠置町笠置西通90番地1', 'https://www.town.kasagi.lg.jp/contents_detail.php?frmId=465', 'Kasagi Town'],
  ['2636530', '和束町役場', '京都府相楽郡和束町大字釜塚小字生水14番地2', 'https://www.town.wazuka.lg.jp/kakukanogoannai/somuka/kakukarenrakusaki/2366.html', 'Wazuka Town'],
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
