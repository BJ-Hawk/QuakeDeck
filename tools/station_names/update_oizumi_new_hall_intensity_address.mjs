import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '1052431');
if (!station || station.nameJa !== '大泉町日の出') throw new Error('Oizumi station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Oizumi placement.');
}
Object.assign(station, {
  facilityNameJa: '大泉町役場新庁舎',
  facilityNameEn: 'Oizumi Town Hall',
  publishedAddressJa: '群馬県邑楽郡大泉町日の出51番1号',
  metadataStatus: 'Municipal completed-relocation notice and official facility address',
  note: "Oizumi Town's official 26 February 2026 system-stoppage page identifies the reporting intensity meter among the disaster equipment being relocated for the town-hall rebuild. The town's 27 February safety-mail announcement explicitly identifies the destination as the new hall. Its 13 March 2026 17:10 safety-mail completion announcement states that the relocation work was completed that day and operation resumed; these two municipal-authored emails are preserved in the third-party anzen.m47.jp archive, identified here as an archive rather than a municipal website. The town's official new-hall opening page publishes 群馬県邑楽郡大泉町日の出51番1号 and confirms the hall's public opening on 7 May 2026. The former 55番1号 address in the older page header is not used. No within-site instrument position is asserted.",
  placementLocalityJa: '大泉町日の出51番1号',
  placementPrecision: 'exact_address',
});
for (const url of [
  'https://www.town.oizumi.gunma.jp/s004/anzen/050/120/20260119112302.html',
  'https://anzen.m47.jp/p10/m273/2538427.html',
  'https://anzen.m47.jp/p10/m273/2545548.html',
  'https://www.town.oizumi.gunma.jp/s008/gyosei/020/010/800/20260422182503.html',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Oizumi new-hall intensity-address update.`);
