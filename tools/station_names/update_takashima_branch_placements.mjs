import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.pref.shiga.lg.jp/file/attachment/31987.pdf';
const activeStationSourceUrl = 'https://www.data.jma.go.jp/hikone/seismo/seismo.html';
const addressSourceUrl = 'https://www.city.takashima.lg.jp/soshiki/seisakubu/kikakukohoka/11/1/683.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2521230', '高島市マキノ町', '高島市役所マキノ支所', '高島市マキノ町沢1410番地'],
  ['2521232', '高島市朽木市場', '高島市役所朽木支所', '高島市朽木市場604番地'],
  ['2521235', '高島市新旭町', '高島市役所', '高島市新旭町北畑565番地'],
  ['2521236', '高島市勝野', '高島市役所高島支所', '高島市勝野215番地'],
  ['2521237', '高島市安曇川町', '高島市役所安曇川支所', '高島市安曇川町田中89番地'],
  ['2521238', '高島市今津町弘川', '高島市役所今津支所', '高島市今津町弘川204番地1']
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
  station.metadataStatus = 'Official Shiga Prefecture network inventory and Takashima City facility records';
  station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, activeStationSourceUrl, addressSourceUrl])];
  station.note = 'Shiga Prefecture explicitly maps this station name to the listed municipal host; the JMA current list confirms the station remains active, and Takashima City publishes the host address.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} Takashima placements.`);
