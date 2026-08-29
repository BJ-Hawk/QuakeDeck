import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://www.pref.shimane.lg.jp/bousai_info/bousai/bousai/bosai_shiryo/sinsai.data/02-2sinnsaihenn_oukyuutaisaku.pdf';
const rows = [
  ['3220144', '松江市鹿島町佐陀本郷', '鹿島支所', '島根県松江市鹿島町佐陀本郷640-1'],
  ['3220145', '松江市玉湯町湯町', '玉湯支所', '島根県松江市玉湯町湯町1793'],
  ['3220146', '松江市島根町加賀', '島根支所', '島根県松江市島根町加賀1175-1'],
  ['3220147', '松江市八束町波入', '八束支所', '島根県松江市八束町波入2060'],
  ['3220148', '松江市東出雲町揖屋', '東出雲支所', '島根県松江市東出雲町揖屋1142'],
  ['3220149', '松江市八雲町西岩坂', '松江市八雲支所', '島根県松江市八雲町西岩坂355-1'],
  ['3220150', '松江市宍道町宍道', '宍道支所', '島根県松江市宍道町宍道885-3'],
  ['3220340', '出雲市多伎町小田', '多伎支所', '島根県出雲市多伎町小田74-1'],
  ['3220341', '出雲市佐田町反辺', '佐田支所', '島根県出雲市佐田町反辺1747-6'],
  ['3220342', '出雲市湖陵町二部', '湖陵支所', '島根県出雲市湖陵町二部1320'],
  ['3220344', '出雲市平田町', '平田支所', '島根県出雲市平田町951-1'],
  ['3220345', '出雲市大社町杵築南', '大社支所', '島根県出雲市大社町杵築南1395'],
  ['3220635', '安来市伯太町東母里', '伯太支所', '島根県安来市伯太町東母里580'],
  ['3220636', '安来市安来町', '安来市役所', '島根県安来市安来町878-2'],
  ['3220939', '雲南市掛合町掛合', '掛合総合センター', '島根県雲南市掛合町掛合1262-1'],
  ['3220940', '雲南市三刀屋町三刀屋', '三刀屋総合センター', '島根県雲南市三刀屋町三刀屋144-1'],
  ['3220941', '雲南市加茂町加茂中', '加茂総合センター', '島根県雲南市加茂町加茂中972-5'],
  ['3220942', '雲南市吉田町吉田', '吉田総合センター', '島根県雲南市吉田町吉田1066'],
  ['3220943', '雲南市木次町里方', '雲南市役所', '島根県雲南市木次町里方521-1'],
  ['3234334', '奥出雲町三成', '奥出雲町役場仁多庁舎', '島根県仁多郡奥出雲町三成358-1'],
  ['3238635', '飯南町頓原', '飯南町保健福祉センター', '島根県飯石郡飯南町頓原2064'],
  ['3238636', '飯南町下赤名', '雲南夢ネット飯南局', '島根県飯石郡飯南町下赤名880'],
  ['3220237', '浜田市金城町下来原', '浜田市金城支所', '島根県浜田市金城町下来原171'],
  ['3220238', '浜田市三隅町三隅', '浜田市三隅支所', '島根県浜田市三隅町三隅1434'],
  ['3220239', '浜田市旭町今市', '浜田市旭支所', '島根県浜田市旭町今市637'],
  ['3220240', '浜田市弥栄町長安本郷', '浜田市弥栄支所', '島根県浜田市弥栄町長安本郷542'],
  ['3220241', '浜田市殿町', '浜田市役所', '島根県浜田市殿町1'],
  ['3220434', '益田市常盤町', '益田市役所', '島根県益田市常盤町1-1'],
  ['3220435', '益田市匹見町匹見', '匹見総合支所', '島根県益田市匹見町匹見イ1260'],
  ['3220436', '益田市美都町都茂', '美都総合支所', '島根県益田市美都町都茂1803-1'],
  ['3220534', '大田市仁摩町仁万', '大田市仁摩支所', '島根県大田市仁摩町仁万562-3'],
  ['3220535', '大田市温泉津町小浜', '大田市温泉津支所', '島根県大田市温泉津町小浜イ486'],
  ['3220536', '大田市大田町', '大田市役所', '島根県大田市大田町大田ロ1111'],
  ['3220733', '江津市江津町', '江津市役所', '島根県江津市江津町1525'],
  ['3220734', '江津市桜江町川戸', '川戸地域コミュニティセンター', '島根県江津市桜江町川戸15-4'],
  ['3244132', '川本町川本', '川本町役場', '島根県邑智郡川本町大字川本271-3'],
  ['3244833', '島根美郷町粕淵', '美郷町役場', '島根県邑智郡美郷町粕淵168'],
  ['3244834', '島根美郷町都賀本郷', '都賀公民館', '島根県邑智郡美郷町都賀本郷163'],
  ['3244934', '邑南町下口羽', '邑南町役場羽須美支所', '島根県邑智郡邑南町下口羽484-1'],
  ['3244935', '邑南町矢上', '邑南町役場', '島根県邑智郡邑南町矢上6000'],
  ['3250133', '津和野町後田', '津和野町津和野庁舎', '島根県鹿足郡津和野町後田ロ64-6'],
  ['3250533', '吉賀町柿木村柿木', '吉賀町柿木庁舎', '島根県鹿足郡吉賀町柿木村柿木500-1'],
  ['3252531', '海士町海士', '海士町役場', '島根県隠岐郡海士町大字海士1490'],
  ['3252836', '隠岐の島町布施', '隠岐の島町役場布施支所', '島根県隠岐郡隠岐の島町布施218-24'],
  ['3252837', '隠岐の島町都万', '隠岐の島町役場都万支所', '島根県隠岐郡隠岐の島町都万2016'],
  ['3252838', '隠岐の島町北方', '隠岐の島町役場五箇支所', '島根県隠岐郡隠岐の島町北方901-1'],
];

const data = JSON.parse(readFileSync(path, 'utf8'));
for (const [code, nameJa, facilityNameJa, publishedAddressJa] of rows) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station || station.prefectureJa !== '島根県' || station.nameJa !== nameJa) {
    throw new Error(`Unexpected station identity for ${code}`);
  }
  if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Station ${code} already has placement data`);
  }
  Object.assign(station, {
    facilityNameJa,
    publishedAddressJa,
    placementPrecision: 'exact_address',
    metadataStatus: 'Official Shimane Prefecture seismic-observation list (2018)',
    note: 'Verified against Shimane Prefecture’s direct seismic-observation facility and address list.',
  });
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

data.coverage.publishedAddresses += rows.length;
data.coverage.exactPlacementAddressUpdates += rows.length;
data.coverage.localityPlacementRecords -= rows.length;
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${rows.length} Shimane stations.`);
