import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '1134230');
if (!station || station.nameJa !== '嵐山町杉山') throw new Error('Ranzan station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Ranzan placement.');
}
Object.assign(station, {
  facilityNameJa: '嵐山町役場',
  facilityNameEn: 'Ranzan Town Hall',
  publishedAddressJa: '埼玉県比企郡嵐山町大字杉山1030番地1',
  metadataStatus: 'Official municipal facility placement',
  note: "Ranzan Town's September 2019 newsletter, printed page 2, explicitly identifies the town's intensity meter at the town hall. Its instrument photograph is captioned 役場西口の職員通用口付近に設置してあります (near the staff entrance at the town hall's west entrance). The town's official website publishes the town-hall address as 埼玉県比企郡嵐山町大字杉山1030番地1.",
  placementLocalityJa: '嵐山町大字杉山1030番地1',
  placementPrecision: 'exact_address',
});
for (const url of [
  'https://www.town.ranzan.saitama.jp/cmsfiles/contents/0000004/4647/02-03.pdf',
  'https://www.town.ranzan.saitama.jp/index.html',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Ranzan Town Hall intensity-address update.`);
