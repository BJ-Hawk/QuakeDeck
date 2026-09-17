import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.pref.shiga.lg.jp/file/attachment/31987.pdf';
const activeStationSourceUrl = 'https://www.data.jma.go.jp/hikone/seismo/seismo.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2521334', '東近江市池庄町', '東近江市役所湖東支所', '東近江市池庄町505番地', 'https://www.city.higashiomi.shiga.jp/shisetsu/1004173/1004178.html'],
  ['2521335', '東近江市市子川原町', '東近江市役所蒲生支所', '東近江市市子川原町676番地', 'https://www.city.higashiomi.shiga.jp/shiseijouhou/soshiki/1004023/1004031/1004037.html'],
  ['2521340', '東近江市五個荘小幡町', '東近江市役所五個荘支所', '東近江市五個荘小幡町318番地', 'https://www.city.higashiomi.shiga.jp/shisetsu/1004173/1004176.html'],
  ['2521341', '東近江市躰光寺町', '東近江市役所能登川支所', '東近江市躰光寺町262番地', 'https://www.city.higashiomi.shiga.jp/shisetsu/1004173/1004179.html']
];

for (const [code, nameJa, facilityNameJa, publishedAddressJa, addressSourceUrl] of updates) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station || station.prefectureJa !== '滋賀県' || station.nameJa !== nameJa) {
    throw new Error(`Unexpected station for ${code}`);
  }
  if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
    throw new Error(`Station ${code} already has placement metadata`);
  }
  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official Shiga Prefecture network inventory and Higashiomi City branch records';
  station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, activeStationSourceUrl, addressSourceUrl])];
  station.note = 'Shiga Prefecture explicitly maps this station name to the municipal branch; the JMA current list confirms the station remains active, and Higashiomi City publishes the branch address.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} Higashiomi branch placements.`);
