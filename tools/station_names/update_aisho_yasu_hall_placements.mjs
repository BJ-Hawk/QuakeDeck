import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.pref.shiga.lg.jp/file/attachment/31987.pdf';
const activeStationSourceUrl = 'https://www.data.jma.go.jp/hikone/seismo/seismo.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2542530', '愛荘町安孫子', '愛荘町役場秦荘庁舎', '滋賀県愛知郡愛荘町安孫子825番地', 'https://www.town.aisho.shiga.jp/soshiki/mirai/6/3/1/11230.html'],
  ['2542531', '愛荘町愛知川', '愛荘町役場愛知川庁舎', '滋賀県愛知郡愛荘町愛知川72番地', 'https://www.town.aisho.shiga.jp/soshiki/mirai/6/3/1/11230.html'],
  ['2521032', '野洲市小篠原', '野洲市役所', '野洲市小篠原2100番地1', 'https://www.city.yasu.lg.jp/soshiki/kouhouhisho/yasu_shisetsu/1450924208281.html']
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
  station.metadataStatus = 'Official Shiga Prefecture network inventory and municipal office records';
  station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, activeStationSourceUrl, addressSourceUrl])];
  station.note = 'Shiga Prefecture explicitly maps this station name to the municipal office; the JMA current list confirms the station remains active, and the municipality publishes the office address.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} Aisho/Yasu placements.`);
