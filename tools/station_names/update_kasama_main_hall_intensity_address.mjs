import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '0821631');
if (!station || station.nameJa !== '笠間市中央') throw new Error('Kasama station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Kasama placement.');
}
Object.assign(station, {
  facilityNameJa: '笠間市役所本所',
  facilityNameEn: 'Kasama City Hall Main Office',
  publishedAddressJa: '茨城県笠間市中央三丁目2番1号',
  metadataStatus: 'Official national field-survey facility placement',
  note: "The National Institute for Land and Infrastructure Management and Building Research Institute's 23 March 2011 field-survey report, section 4-2-2, page 8 and photograph 23, directly documents the local-government intensity meter at Kasama City Hall. Its expanded report explicitly distinguishes the main city hall from the Kasama branch office. Kasama City's official main-office guide publishes 茨城県笠間市中央三丁目2番1号, also the address in the city's contemporary 2011 office guide. This is the Chuo station; the branch-office stations are not assigned from this evidence.",
  placementLocalityJa: '笠間市中央三丁目2番1号',
  placementPrecision: 'exact_address',
});
for (const url of [
  'https://www.nilim.go.jp/lab/bbg/saigai/h23tohoku/110315kentiku.pdf',
  'https://www.kenken.go.jp/japanese/contents/topics/20110311/pdf/quickreport/0311quickreport_54.pdf',
  'https://www.city.kasama.lg.jp/page/page000307.html',
  'https://www.city.kasama.lg.jp/data/doc/1315359550_doc_13_0.pdf',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Kasama main-hall intensity-address update.`);
