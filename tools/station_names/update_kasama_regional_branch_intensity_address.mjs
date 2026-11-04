import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '0821634');
if (!station || station.nameJa !== '笠間市笠間') throw new Error('Kasama branch station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Kasama branch placement.');
}
Object.assign(station, {
  facilityNameJa: '笠間市役所笠間支所',
  facilityNameEn: 'Kasama City Hall Kasama Regional Branch',
  publishedAddressJa: '茨城県笠間市笠間1532番地',
  metadataStatus: 'Official completed-relocation facility placement',
  note: "Kasama City's fiscal 2013 accounts-review record, printed page 360 (PDF page 38), reports completed expenditure of 3,118,500 yen for relocation of the prefectural disaster-system intensity meter and related equipment as part of the Kasama Branch move, alongside expenditure for opening the new branch. This is an executed-expenditure record, not the earlier proposed relocation budget. The city's current transport guide and English office directory identify Kasama Regional Branch at 茨城県笠間市笠間1532番地. The older branch at 石井717 is not assigned to the current 笠間市笠間 station.",
  placementLocalityJa: '笠間市笠間1532番地',
  placementPrecision: 'exact_address',
});
for (const url of [
  'https://www.city.kasama.lg.jp/data/doc/1414557015_doc_56_8.pdf',
  'https://www.city.kasama.lg.jp/page/page000167.html',
  'https://www.city.kasama.lg.jp/page/page011843.html',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Kasama regional-branch intensity-address update.`);
