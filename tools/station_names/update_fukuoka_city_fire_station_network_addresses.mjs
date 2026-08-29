import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sources = [
  'https://www.city.fukuoka.lg.jp/shimin/bousai/bousai/documents/chiboukei_honpen2024.pdf',
  'https://www.city.fukuoka.lg.jp/syobo/somu/about/syozaiti.html'
];
const updates = {
  '4013131': ['東消防署', '福岡市東区千早4丁目15番1号'],
  '4013231': ['博多消防署', '福岡市博多区博多駅前4丁目19番7号'],
  '4013430': ['南消防署', '福岡市南区塩原2丁目6番11号'],
  '4013530': ['西消防署', '福岡市西区今宿東1丁目7番12号'],
  '4013630': ['城南消防署', '福岡市城南区神松寺2丁目19番12号'],
  '4013730': ['早良消防署', '福岡市早良区百道浜1丁目3番1号']
};
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const [code, [facilityNameJa, publishedAddressJa]] of Object.entries(updates)) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station) throw new Error(`Station ${code} was not found.`);
  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.metadataStatus = 'Fukuoka City disaster plan and Fire Bureau facility address';
  station.note = 'Fukuoka City’s disaster plan directly places its network meters at the Fire Bureau and each fire station except Chuo Fire Station; the official Fire Bureau page publishes this station’s facility and address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), ...sources])];
  delete station.placementLocalityJa;
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
