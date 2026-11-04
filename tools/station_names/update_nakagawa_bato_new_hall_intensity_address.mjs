import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '0941132');
if (!station || station.nameJa !== '栃木那珂川町馬頭') throw new Error('Nakagawa Bato station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Nakagawa Bato placement.');
}
Object.assign(station, {
  facilityNameJa: '那珂川町役場本庁舎',
  facilityNameEn: 'Nakagawa Town Hall Main Office',
  publishedAddressJa: '栃木県那須郡那珂川町馬頭555番地',
  placementLocalityJa: '那珂川町馬頭555番地',
  placementPrecision: 'exact_address',
  metadataStatus: 'Official completed new-hall network relocation and facility address',
  note: "Nakagawa Town's fiscal 2017 major-policy results report, printed page 3 (PDF page 5), section 3 町有財産管理事業, states that the new hall was completed and opened for full operation on 10 October 2017. Its completed new-hall project expenditure explicitly includes 栃木県震度情報ネットワークシステム移設工事 (2,052 thousand yen). JMA's 22 February 2018 station-code notice, PDF page 3, separately identifies the relocated Bato observation point as replacement code 0941132 for 0941130. The town's current office directory identifies the main hall at 馬頭555, and its office guide publishes the full 栃木県那須郡那珂川町馬頭555番地 address. The report is cited at its current municipal upload URL; its former /06seisaku/ URL returned 404 during verification. This evidence is not applied to the separate Ogawa station, whose relocation destination remains unverified. No within-site instrument position is asserted.",
});
for (const url of [
  'https://www.town.tochigi-nakagawa.lg.jp/manage/contents/upload/69325ffd767d7.pdf',
  'https://xml.kishou.go.jp/20180222_jmaxml_info.pdf',
  'https://www.town.tochigi-nakagawa.lg.jp/0082/info-0000000484-0.html',
  'https://www.town.tochigi-nakagawa.lg.jp/0001/genre1-0-001.html',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Nakagawa Bato new-hall intensity-address update.`);
