import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const higashihiroshimaSource = 'https://www.city.higashihiroshima.lg.jp/material/files/group/5/siryouhen20250530.pdf';
const hatsukaichiSource = 'https://www.city.hatsukaichi.hiroshima.jp/uploaded/attachment/87968.pdf';
const updates = new Map([
  ['3421235', ['福富支所', '広島県東広島市福富町久芳1545番地1', higashihiroshimaSource]],
  ['3421236', ['豊栄支所', '広島県東広島市豊栄町鍛冶屋963番地2', higashihiroshimaSource]],
  ['3421237', ['河内支所', '広島県東広島市河内町中河内1166番地', higashihiroshimaSource]],
  ['3421238', ['あきつ世代交流センター', '広島県東広島市安芸津町三津5556番地1', higashihiroshimaSource]],
  ['3421330', ['廿日市市役所', '広島県廿日市市下平良一丁目11番1号', hatsukaichiSource]],
  ['3421335', ['廿日市市役所宮島支所', '広島県廿日市市宮島町1165番地6', hatsukaichiSource]],
  ['3421336', ['廿日市市役所佐伯支所', '広島県廿日市市津田1989番地', hatsukaichiSource]],
  ['3421337', ['廿日市市役所吉和支所', '広島県廿日市市吉和1186番地', hatsukaichiSource]],
]);
const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update || station.publishedAddressJa || station.facilityNameJa) continue;
  const [facilityNameJa, publishedAddressJa, source] = update;
  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'The municipal disaster plan lists this intensity-meter host facility and its exact address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  updated += 1;
}
if (updated) writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updated} station(s).`);
