import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://www.pref.wakayama.lg.jp/prefg/011400/d00153903_d/fil/03-4_siryou_3.pdf';
const updates = new Map([
  ['3020233', ['海南市役所', '和歌山県海南市南赤坂11']],
  ['3020234', ['海南市消防本部下津消防署', '和歌山県海南市下津514-2']],
  ['3020330', ['橋本市役所', '和歌山県橋本市東家1-1-1']],
  ['3020332', ['橋本市高野口公民館', '和歌山県橋本市高野口町名倉813-2']],
  ['3020833', ['紀の川市役所貴志川支所', '和歌山県紀の川市貴志川町神戸327-1']],
  ['3020835', ['紀の川市役所', '和歌山県紀の川市西大井338']],
  ['3020836', ['紀の川市役所桃山支所', '和歌山県紀の川市桃山町元376']],
  ['3020930', ['岩出市役所', '和歌山県岩出市西野209']],
  ['3030432', ['紀美野町役場美里支所', '和歌山県海草郡紀美野町神野市場226-1']],
  ['3030433', ['紀美野町消防本部', '和歌山県海草郡紀美野町下佐々1609-2']],
  ['3034130', ['地域福祉センター', '和歌山県伊都郡かつらぎ町丁ノ町2338-2']],
  ['3034132', ['かつらぎ町役場花園支所', '和歌山県伊都郡かつらぎ町梁瀬645']],
  ['3034330', ['九度山町役場', '和歌山県伊都郡九度山町九度山1190']],
  ['3036131', ['湯浅町役場', '和歌山県有田郡湯浅町青木668-1']],
  ['3036231', ['広川町役場', '和歌山県有田郡広川町広1500']],
  ['3036632', ['有田川町役場吉備庁舎', '和歌山県有田郡有田川町下津野2018-4']],
  ['3036633', ['有田川町役場金屋庁舎', '和歌山県有田郡有田川町大字中井原136-2']],
  ['3038130', ['美浜町役場', '和歌山県日高郡美浜町和田1138-278']],
  ['3038230', ['日高町役場', '和歌山県日高郡日高町高家626']],
  ['3038330', ['由良町役場', '和歌山県日高郡由良町里1220-1']],
  ['3039032', ['印南町役場', '和歌山県日高郡印南町印南2570']],
  ['3039131', ['みなべ町役場第1庁舎', '和歌山県日高郡みなべ町芝742']],
  ['3039233', ['日高川町役場本庁', '和歌山県日高郡日高川町土生160']],
  ['3039234', ['日高川町役場中津支所', '和歌山県日高郡日高川町高津尾29']],
  ['3039235', ['日高川町保健福祉センター', '和歌山県日高郡日高川町大字川原河264']],
  ['3020630', ['田辺市役所中辺路行政局', '和歌山県田辺市中辺路町栗栖川396-1']],
  ['3020632', ['田辺市役所本宮行政局', '和歌山県田辺市本宮町本宮219']],
  ['3020633', ['田辺市役所大塔行政局', '和歌山県田辺市鮎川2567-1']],
  ['3020635', ['田辺市役所龍神行政局', '和歌山県田辺市龍神村西376']],
  ['3020636', ['田辺市役所', '和歌山県田辺市東山一丁目5番1号']],
  ['3020730', ['新宮市役所熊野川行政局', '和歌山県新宮市熊野川町日足324']],
  ['3040130', ['白浜町役場日置川事務所', '和歌山県西牟婁郡白浜町日置980-1']],
  ['3040430', ['上富田町役場', '和歌山県西牟婁郡上富田町朝来763']],
  ['3042133', ['那智勝浦町消防・防災センター', '和歌山県東牟婁郡那智勝浦町大字天満1244-1']],
  ['3042730', ['北山村役場', '和歌山県東牟婁郡北山村大沼42']],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  const placement = updates.get(station.code);
  if (!placement) continue;
  if (station.facilityNameJa || station.publishedAddressJa) {
    throw new Error(`${station.code} already has placement data.`);
  }
  station.facilityNameJa = placement[0];
  station.publishedAddressJa = placement[1];
  station.metadataStatus = 'Official prefectural seismic-network address';
  station.note = "Wakayama Prefecture's official earthquake-observation table directly lists this station, the host site, and the address.";
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
  updated += 1;
}
if (updated !== updates.size) {
  throw new Error(`Updated ${updated} records; expected ${updates.size}.`);
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updated} Wakayama stations.`);
