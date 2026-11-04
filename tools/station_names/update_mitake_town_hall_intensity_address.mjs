import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '2152130');
if (!station || station.nameJa !== '御嵩町御嵩') throw new Error('Mitake station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Mitake placement.');
}
Object.assign(station, {
  facilityNameJa: '御嵩町役場敷地',
  facilityNameEn: 'Mitake Town Hall grounds',
  publishedAddressJa: '岐阜県可児郡御嵩町御嵩1239番地1',
  placementLocalityJa: '御嵩町御嵩1239番地1',
  placementPrecision: 'exact_address',
  metadataStatus: 'Official disaster-plan instrument placement and municipal address',
  note: "Mitake Town's earthquake-response plan, chapter 3 section 1, printed page 41 (PDF page 1), explicitly identifies the prefectural earthquake-information-network intensity meter as installed on the Town Hall grounds. The town's current official new-hall coordination page publishes the existing Town Hall address 岐阜県可児郡御嵩町御嵩1239番地1. The town's 1 October 2026 notice announces redistribution of some office functions to temporary premises from 13 October; it does not identify a new instrument location. The record identifies the existing hall grounds documented by the placement source, not a particular office room, temporary office, or the future new-hall site. No within-site meter position or completed instrument move is asserted.",
});
for (const url of [
  'https://www.town.mitake.lg.jp/wp-content/uploads/1d197b71716152613fe6b165592a2023.pdf',
  'https://www.town.mitake.lg.jp/portal/town/administration/new-government-building/',
  'https://www.town.mitake.lg.jp/news/news0107851/',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Mitake Town Hall intensity-address update.`);
