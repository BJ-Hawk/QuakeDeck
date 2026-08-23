import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.fdma.go.jp/bousaikeikaku/shikoku/ehime/items/05_ehime_shiryou.pdf';
const placements = new Map([
  ['3820532', ['新居浜市別子山支所', '愛媛県新居浜市別子山甲347番地1']],
  ['3820630', ['西条市消防本部', '愛媛県西条市新田183番地1']],
  ['3820632', ['西条市丹原総合支所', '愛媛県西条市丹原町池田1733番地1']],
  ['3820633', ['西条市小松総合支所', '愛媛県西条市小松町新屋敷甲496番地']],
  ['3820332', ['宇和島市津島支所', '愛媛県宇和島市津島町岩松甲471番地']],
  ['3820333', ['宇和島市吉田支所', '愛媛県宇和島市吉田町西小路7番地']],
  ['3820334', ['宇和島市三間支所', '愛媛県宇和島市三間町宮野下835番地']],
  ['3820430', ['八幡浜市保内庁舎', '愛媛県八幡浜市保内町宮内1番耕地260番地']],
  ['3820730', ['大洲市役所', '愛媛県大洲市大洲690番地1']],
  ['3820731', ['大洲市河辺支所', '愛媛県大洲市河辺町植松548番地']],
  ['3821031', ['伊予市双海地域事務所', '愛媛県伊予市双海町上灘甲5821番地6']],
  ['3821032', ['伊予市中山地域事務所', '愛媛県伊予市中山町出渕2番耕地138番地1']],
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
