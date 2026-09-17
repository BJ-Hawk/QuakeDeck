import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.pref.shiga.lg.jp/file/attachment/31987.pdf';
const activeStationSourceUrl = 'https://www.data.jma.go.jp/hikone/seismo/seismo.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['2520230', '彦根市元町', '彦根市役所', '滋賀県彦根市元町4番2号', 'https://www.city.hikone.lg.jp/kurashi/shisetsu/9/about.html'],
  ['2544230', '甲良町在士', '甲良町役場', '滋賀県犬上郡甲良町在士353-1', 'https://www.kouratown.jp/gyosei/index.html'],
  ['2538331', '滋賀日野町河原', '日野町役場', '滋賀県蒲生郡日野町河原一丁目1番地', 'https://www.town.shiga-hino.lg.jp/soshiki_list.html'],
  ['2538431', '竜王町小口', '竜王町役場', '滋賀県蒲生郡竜王町大字小口3番地', 'https://www.town.ryuoh.shiga.jp/access/access/access.html']
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
  station.note = 'Shiga Prefecture explicitly maps this station name to the municipal hall; the JMA current list confirms the station remains active, and the municipality publishes the hall address.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} Shiga municipal-hall placements.`);
