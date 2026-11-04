import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '1124530');
if (!station || station.nameJa !== 'ふじみ野市福岡') throw new Error('Fujimino station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Fujimino placement.');
}
Object.assign(station, {
  facilityNameJa: 'ふじみ野市役所本庁舎',
  facilityNameEn: 'Fujimino City Hall Main Office',
  publishedAddressJa: '埼玉県ふじみ野市福岡1-1-1',
  metadataStatus: 'Official municipal facility placement',
  note: "Fujimino City's official mayoral town-meeting record of 29 November 2012 identifies the intensity meter behind the main city-hall building and says its observations are transmitted to the prefecture and meteorological agency. The 2 November 2024 mayoral response (FY2024 compilation, item 318, page 48) confirms an intensity meter at city hall. The city publishes the main-office address as ふじみ野市福岡1-1-1. The older behind-the-building description is historical; no current position within the grounds is asserted. This records the Fukuoka station only.",
  placementLocalityJa: 'ふじみ野市福岡1-1-1',
  placementPrecision: 'exact_address',
});
for (const url of [
  'https://www.city.fujimino.saitama.jp/material/files/group/3/1129.pdf',
  'https://www.city.fujimino.saitama.jp/material/files/group/3/r6_tm_matome.pdf',
  'https://www.city.fujimino.saitama.jp/bosai_bohan/kyukyu/4956.html',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Fujimino main-hall intensity-address update.`);
