import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '0820731');
if (!station || station.nameJa !== '結城市中央町') throw new Error('Yuki station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Yuki placement.');
}
Object.assign(station, {
  facilityNameJa: '結城市役所',
  facilityNameEn: 'Yuki City Hall',
  publishedAddressJa: '茨城県結城市中央町二丁目3番地',
  metadataStatus: 'Official municipal relocation and facility placement',
  note: "Yuki City's 15 November 2020 newsletter explicitly links its intensity-meter relocation to the move to the new city hall, and identifies the station-name change from 結城市結城 to 結城市中央町 effective 29 October 2020. The city's new-hall brochure publishes 茨城県結城市中央町二丁目3番地. A May 2022 city newsletter confirms the operating intensity-information feed is affected by the city-hall electrical inspection. The old city-hall address in the 2020 newsletter header is not used.",
  placementLocalityJa: '結城市中央町二丁目3番地',
  placementPrecision: 'exact_address',
});
for (const url of [
  'https://www.city.yuki.lg.jp/data/doc/1605316556_doc_9_0.pdf',
  'https://www.city.yuki.lg.jp/data/doc/1605144535_doc_175_0.pdf',
  'https://www.city.yuki.lg.jp/data/doc/1651027373_doc_9_0.pdf',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Yuki City Hall intensity-address update.`);
