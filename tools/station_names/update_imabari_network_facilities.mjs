import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.fdma.go.jp/bousaikeikaku/shikoku/ehime/items/05_ehime_shiryou.pdf';
const placements = new Map([
  ['3820230', ['今治市朝倉支所', '愛媛県今治市朝倉北甲397番地']],
  ['3820231', ['今治市玉川支所', '愛媛県今治市玉川町三反地甲10番地']],
  ['3820233', ['今治市大西支所', '愛媛県今治市大西町宮脇甲506番地1']],
  ['3820234', ['今治市菊間支所', '愛媛県今治市菊間町浜822番地']],
  ['3820235', ['今治市吉海支所', '愛媛県今治市吉海町八幡137番地']],
  ['3820236', ['今治市宮窪支所', '愛媛県今治市宮窪町宮窪2668番地']],
  ['3820238', ['今治市上浦支所', '愛媛県今治市上浦町井口6605番地']],
  ['3820239', ['今治市大三島支所', '愛媛県今治市大三島町宮浦5708番地']],
  ['3820242', ['今治市関前支所', '愛媛県今治市関前岡村甲732番地']],
  ['3820243', ['今治市波方支所', '愛媛県今治市波方町樋口甲253番地']],
  ['3820244', ['今治市伯方支所', '愛媛県今治市伯方町木浦甲1235番地']],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const [code, [facility, address]] of placements) {
  const target = data.stations.find((entry) => entry.code === code);
  if (!target) throw new Error(`Missing station ${code}`);
  if (target.publishedAddressJa || target.facilityNameJa) continue;
  target.facilityNameJa = facility;
  target.publishedAddressJa = address;
  target.metadataStatus = 'Official FDMA facility address';
  target.note = 'The FDMA-hosted Ehime plan lists this prefectural intensity meter’s host facility and installation address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), source])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
