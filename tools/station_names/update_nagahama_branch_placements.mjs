import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.pref.shiga.lg.jp/file/attachment/31987.pdf';
const activeStationSourceUrl = 'https://www.data.jma.go.jp/hikone/seismo/seismo.html';
const addressSourceUrl = 'https://www.city.nagahama.lg.jp/cmsfiles/contents/0000002/2994/p14-15.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2520334', '長浜市湖北町速水', '長浜市役所湖北支所', '長浜市湖北町速水2745'],
  ['2520336', '長浜市木之本町木之本', '長浜市役所木之本支所', '長浜市木之本町木之本1757-2'],
  ['2520337', '長浜市余呉町中之郷', '長浜市役所余呉支所', '長浜市余呉町中之郷958'],
  ['2520339', '長浜市西浅井町大浦', '長浜市役所西浅井支所', '長浜市西浅井町大浦2590']
];

for (const [code, nameJa, facilityNameJa, publishedAddressJa] of updates) {
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
  station.metadataStatus = 'Official Shiga Prefecture network inventory and Nagahama City facility records';
  station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, activeStationSourceUrl, addressSourceUrl])];
  station.note = 'Shiga Prefecture explicitly maps this station name to the listed municipal branch; the JMA current list confirms the station remains active, and Nagahama City publishes the branch address.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} Nagahama branch placements.`);
