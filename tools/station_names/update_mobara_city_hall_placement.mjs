import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.city.mobara.chiba.jp/faq/faq_detail.php?co=ser&frmId=148';
const addressSourceUrl = 'https://www.city.mobara.chiba.jp/0000000121.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '1221030');
if (!station || station.prefectureJa !== '千葉県' || station.nameJa !== '茂原市道表') {
  throw new Error('Unexpected station for 1221030');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 1221030 already has placement metadata');
}

station.facilityNameJa = '茂原市役所';
station.publishedAddressJa = '千葉県茂原市道表1番地';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Mobara City intensity-meter and City Hall records';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, addressSourceUrl])];
station.note = 'Mobara City states that its intensity meter is installed on the east side of the Main City Hall grounds and automatically sends the observations to JMA; this is the only catalogue station in Mobara, named for the same locality as City Hall.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 1221030 Mobara City Hall.');
