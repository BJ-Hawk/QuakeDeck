import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.fdma.go.jp/bousaikeikaku/shikoku/ehime/items/05_ehime_shiryou.pdf';
const placements = new Map([
  ['3821332', ['四国中央市土居総合支所', '愛媛県四国中央市土居町入野178番地']],
  ['3821333', ['四国中央市川之江文化センター', '愛媛県四国中央市金生町下分791番地2']],
  ['3821334', ['四国中央市新宮総合支所', '愛媛県四国中央市新宮町新宮461番地']],
  ['3835630', ['上島町魚島総合支所', '愛媛県越智郡上島町魚島1番耕地1362番地1']],
  ['3835632', ['上島町生名総合支所', '愛媛県越智郡上島町生名621番地1']],
  ['3835635', ['上島町弓削総合支所', '愛媛県越智郡上島町弓削下弓削210番地']],
  ['3835636', ['上島町岩城総合支所', '愛媛県越智郡上島町岩城1427番地2']],
  ['3820130', ['松山市中島支所', '愛媛県松山市中島大浦1626番地']],
  ['3821530', ['東温市役所', '愛媛県東温市見奈良530番地1']],
  ['3838630', ['久万高原町役場', '愛媛県上浮穴郡久万高原町久万212番地']],
  ['3838632', ['久万高原町柳谷支所', '愛媛県上浮穴郡久万高原町柳井川923番地']],
  ['3838633', ['久万高原町面河支所', '愛媛県上浮穴郡久万高原町渋草2431番地']],
  ['3840130', ['松前町役場', '愛媛県伊予郡松前町大字筒井631番地']],
  ['3840230', ['砥部町役場', '愛媛県伊予郡砥部町宮内1392番地']],
  ['3821433', ['西予市城川総合支所', '愛媛県西予市城川町下相945番地']],
  ['3821434', ['西予市役所', '愛媛県西予市宇和町卯之町3丁目434番地']],
  ['3821435', ['西予市三瓶総合支所', '愛媛県西予市三瓶町朝立1番耕地360番地1']],
  ['3821436', ['西予市明浜支所', '愛媛県西予市明浜町高山甲3420番地']],
  ['3842230', ['内子町内子分庁', '愛媛県喜多郡内子町内子1515番地']],
  ['3842232', ['内子町役場', '愛媛県喜多郡内子町平岡甲168番地']],
  ['3842233', ['内子町小田支所', '愛媛県喜多郡内子町小田81番地']],
  ['3844231', ['伊方町役場', '愛媛県西宇和郡伊方町湊浦1993番地1']],
  ['3844232', ['伊方町瀬戸総合支所', '愛媛県西宇和郡伊方町三机乙3003番地6']],
  ['3848431', ['松野町役場', '愛媛県北宇和郡松野町大字松丸343番地']],
  ['3848831', ['鬼北町日吉支所', '愛媛県北宇和郡鬼北町大字下鍵山463番地']],
  ['3848832', ['鬼北町防災センター', '愛媛県北宇和郡鬼北町大字近永1214番地']],
  ['3850630', ['愛南町内海支所', '愛媛県南宇和郡愛南町柏497番地']],
  ['3850631', ['愛南町御荘支所', '愛媛県南宇和郡愛南町御荘平城3063番地']],
  ['3850633', ['愛南町一本松支所', '愛媛県南宇和郡愛南町広見3535番地']],
  ['3850634', ['愛南町役場', '愛媛県南宇和郡愛南町城辺甲2420番地']],
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
