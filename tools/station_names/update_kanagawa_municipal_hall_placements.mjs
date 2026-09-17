import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  {
    code: '1421731',
    nameJa: '南足柄市関本',
    facilityNameJa: '南足柄市役所',
    publishedAddressJa: '神奈川県南足柄市関本440番地',
    placementSourceUrl: 'https://www.city.minamiashigara.kanagawa.jp/kurashi/bouan/boutai/torikumi/bousaisoshiki_katudou.html',
    addressSourceUrl: 'https://www.city.minamiashigara.kanagawa.jp/shisetsu/service/siyakusyo.html',
    note: 'Minamiashigara City states that its City Hall intensity meter is the trigger source for disaster-response procedures; this is the sole catalogue station in the municipality and its published label is Minamiashigara City Sekimoto.',
  },
  {
    code: '1436131',
    nameJa: '中井町比奈窪',
    facilityNameJa: '中井町役場',
    publishedAddressJa: '神奈川県足柄上郡中井町比奈窪56番地',
    placementSourceUrl: 'https://www.town.nakai.kanagawa.jp/material/files/group/6/H19-8.pdf',
    addressSourceUrl: 'https://www.town.nakai.kanagawa.jp/soshiki/chiikibosaikachiikijohohan/nakaimachinoshokai/index.html',
    note: 'Nakai Town states that its intensity meter is installed inside the Town Hall building; this is the sole catalogue station in the municipality and its published label is Nakai Town Hinakubo.',
  },
  {
    code: '1436330',
    nameJa: '松田町松田惣領',
    facilityNameJa: '松田町役場',
    publishedAddressJa: '神奈川県足柄上郡松田町松田惣領2037番地',
    placementSourceUrl: 'https://town.matsuda.kanagawa.jp/soshiki/3/anshinlog2011.html',
    addressSourceUrl: 'https://town.matsuda.kanagawa.jp/soshiki/1/syozaichi.html',
    note: 'Matsuda Town identifies its Town Hall intensity meter in its official disaster bulletin; this is the sole catalogue station in the municipality and its published label is Matsuda Town Matsuda-Soryo.',
  },
];

for (const update of updates) {
  const station = data.stations.find((entry) => entry.code === update.code);
  if (!station || station.prefectureJa !== '神奈川県' || station.nameJa !== update.nameJa || station.municipalityStationCount !== 1) {
    throw new Error(`Unexpected station for ${update.code}`);
  }
  if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
    throw new Error(`Station ${update.code} already has placement metadata`);
  }
  station.facilityNameJa = update.facilityNameJa;
  station.publishedAddressJa = update.publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official Kanagawa municipal seismic-intensity and facility records';
  station.sourceUrls = [...new Set([...station.sourceUrls, update.placementSourceUrl, update.addressSourceUrl])];
  station.note = update.note;
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} Kanagawa municipal-hall stations.`);
