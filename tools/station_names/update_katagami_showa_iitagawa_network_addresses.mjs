import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const sources = [
  'https://www.city.katagami.lg.jp/material/files/group/13/20120511-093204.pdf',
  'https://www.city.katagami.lg.jp/gyosei/gyoseijoho/shiyakusho/4855.html',
];
const updates = [
  [
    '0521130',
    '潟上市昭和大久保',
    '潟上市役所昭和庁舎（現 昭和出張所）',
    '秋田県潟上市昭和大久保字堤の上1番地3',
  ],
  [
    '0521131',
    '潟上市飯田川下虻川',
    '潟上市役所飯田川庁舎（現 飯田川出張所）',
    '秋田県潟上市飯田川下虻川字八ツ口70',
  ],
];

for (const [code, stationName, facility, address] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.nameJa !== stationName) throw new Error(`${code}: expected ${stationName}, got ${station.nameJa}`);
  if (station.facilityNameJa || station.publishedAddressJa) throw new Error(`${code}: placement unexpectedly present`);

  station.facilityNameJa = facility;
  station.publishedAddressJa = address;
  station.metadataStatus = 'Official municipal seismic-network placement and address';
  station.note = 'Katagami City’s official council record identifies the prefecture-installed intensity meter at this office; the city confirms the same building is the current branch office at the published address.';
  station.placementPrecision = 'exact_address';
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), ...sources])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
