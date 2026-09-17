import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.town.kawamata.lg.jp/uploaded/attachment/21630.pdf';
const addressSourceUrl = 'https://www.town.kawamata.lg.jp/site/chosei-shisetsu/list72-547.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '0730832');
if (!station || station.prefectureJa !== '福島県' || station.nameJa !== '川俣町五百田') {
  throw new Error('Unexpected station for 0730832');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 0730832 already has placement metadata');
}

station.facilityNameJa = '川俣町役場';
station.publishedAddressJa = '福島県伊達郡川俣町字五百田30番地';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Kawamata Town disaster plan and Town Hall record';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, addressSourceUrl])];
station.note = 'Kawamata Town\'s current disaster plan states that the Town Hall has the intensity meter; this is the only catalogue station in Kawamata, named for the same Gohyakuda locality as the published Town Hall address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 0730832 Kawamata Town Hall.');
