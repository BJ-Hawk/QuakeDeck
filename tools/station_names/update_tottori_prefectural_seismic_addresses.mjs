import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://www.pref.tottori.lg.jp/secure/182101/230316_shindokei_tottori.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  ['3120131', '鳥取市福部町細川', '福部町総合支所', '鳥取市福部町細川668'],
  ['3120133', '鳥取市用瀬町用瀬', '用瀬町総合支所', '鳥取市用瀬町用瀬832'],
  ['3120134', '鳥取市佐治町加瀬木', '佐治町総合支所', '鳥取市佐治町加瀬木2519-3'],
  ['3120136', '鳥取市鹿野町鹿野', '鹿野町総合支所', '鳥取市鹿野町鹿野1517'],
  ['3120138', '鳥取市国府町宮下', '国府町総合支所', '鳥取市国府町宮下1221'],
  ['3120139', '鳥取市気高町浜村', '気高町総合支所', '鳥取市気高町浜村282-1'],
  ['3120140', '鳥取市河原町渡一木', '河原町総合支所', '鳥取市河原町渡一木277'],
  ['3120141', '鳥取市青谷町青谷', '青谷町総合支所', '鳥取市青谷町青谷667'],
  ['3132530', '鳥取若桜町若桜', '若桜町役場', '八頭郡若桜町若桜801-5'],
  ['3132930', '八頭町郡家', '八頭町役場', '八頭郡八頭町郡家493'],
  ['3132931', '八頭町船岡', '八頭町役場船岡庁舎', '八頭郡八頭町船岡539'],
  ['3132932', '八頭町北山', '八頭町役場八東庁舎', '八頭郡八頭町北山63-1'],
  ['3120330', '倉吉市関金町大鳥居', '倉吉市役所関金庁舎', '倉吉市関金町大鳥居193-1'],
  ['3136431', '三朝町大瀬', '三朝町役場', '東伯郡三朝町大瀬999-2'],
  ['3137030', '湯梨浜町久留', '湯梨浜町役場', '東伯郡湯梨浜町久留19-1'],
  ['3137032', '湯梨浜町龍島', '湯梨浜町役場東郷庁舎', '東伯郡湯梨浜町龍島500'],
  ['3137033', '湯梨浜町泊', '湯梨浜町役場泊庁舎', '東伯郡湯梨浜町泊534-1'],
  ['3137131', '琴浦町赤碕', '琴浦町役場分庁舎', '東伯郡琴浦町赤碕1140-1'],
  ['3137132', '琴浦町徳万', '琴浦町役場', '東伯郡琴浦町徳万591-2'],
  ['3137231', '北栄町由良宿', '北栄町役場', '東伯郡北栄町由良宿423-1'],
  ['3137232', '北栄町土下', '北栄町役場北条支所旧庁舎', '東伯郡北栄町土下112'],
  ['3120230', '米子市淀江町', '米子市役所淀江支所', '米子市淀江町西原1129-1'],
  ['3120432', '境港市竹内町', '余子駅前公園', '境港市竹内町1513-1'],
  ['3138431', '日吉津村日吉津', '日吉津村役場', '西伯郡日吉津村日吉津872-15'],
  ['3138631', '大山町御来屋', '大山町役場', '西伯郡大山町御来屋328'],
  ['3138633', '大山町末長', '大山町役場大山支所', '西伯郡大山町末長500'],
  ['3138634', '大山町赤坂', '大山町役場中山支所', '西伯郡大山町赤坂66'],
  ['3138930', '鳥取南部町法勝寺', '南部町役場', '西伯郡南部町法勝寺377-1'],
  ['3138931', '鳥取南部町天萬', '南部町役場天萬庁舎', '西伯郡南部町天萬558'],
  ['3139030', '伯耆町吉長', '伯耆町役場', '西伯郡伯耆町吉長37-3'],
  ['3139031', '伯耆町溝口', '伯耆町役場溝口分庁舎', '西伯郡伯耆町溝口647'],
  ['3140131', '日南町霞', '日南町総合文化センター', '日野郡日南町霞785'],
  ['3140231', '鳥取日野町根雨', '日野町役場', '日野郡日野町根雨101'],
  ['3140332', '江府町江尾', '江府町役場', '日野郡江府町江尾1717番地1'],
];

for (const [code, nameJa, facilityNameJa, publishedAddressJa] of updates) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station || station.prefectureJa !== '鳥取県' || station.nameJa !== nameJa) {
    throw new Error(`Unexpected station for ${code}`);
  }
  if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
    throw new Error(`Station ${code} already has placement metadata`);
  }
  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official Tottori Prefecture seismic-observation list (2023)';
  station.sourceUrls = [...new Set([...station.sourceUrls, sourceUrl])];
  station.note = 'Verified against Tottori Prefecture’s direct seismic-observation facility and address list.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} Tottori stations.`);
