import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.pref.shiga.lg.jp/file/attachment/31987.pdf';
const activeStationSourceUrl = 'https://www.data.jma.go.jp/hikone/seismo/seismo.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2544130', '豊郷町石畑', '豊郷町役場', '滋賀県犬上郡豊郷町石畑375', 'https://www.town.toyosato.shiga.jp/soshiki_list.html'],
  ['2544330', '多賀町多賀', '多賀町役場', '滋賀県犬上郡多賀町多賀324', 'https://www.town.taga.lg.jp/soshiki/zeimujumin/1/3/5/528.html']
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
  station.metadataStatus = 'Official Shiga Prefecture network inventory and municipal hall records';
  station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, activeStationSourceUrl, addressSourceUrl])];
  station.note = 'Shiga Prefecture explicitly maps this station name to the town hall; the JMA current list confirms the station remains active, and the town publishes the hall address.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} town-hall placements.`);
