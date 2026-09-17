import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.pref.shiga.lg.jp/file/attachment/31987.pdf';
const activeStationSourceUrl = 'https://www.data.jma.go.jp/hikone/seismo/seismo.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2521342', '東近江市八日市緑町', '東近江市役所', '東近江市八日市緑町10番5号', 'https://www.city.higashiomi.shiga.jp/shiseijouhou/soshiki/1004023/1004024.html'],
  ['2521338', '東近江市妹町', '愛東福祉センターじゅぴあ', '東近江市妹町29番地', 'https://www.city.higashiomi.shiga.jp/_res/projects/default_project/_page_/001/007/951/11sokora.pdf']
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
  station.metadataStatus = 'Official Shiga Prefecture network inventory and Higashiomi City facility records';
  station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, activeStationSourceUrl, addressSourceUrl])];
  station.note = 'Shiga Prefecture explicitly maps this station name to the listed host; the JMA current list confirms the station remains active, and Higashiomi City publishes the host address.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} Higashiomi placements.`);
