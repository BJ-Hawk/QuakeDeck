import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20230619053618_0_Z__J_JPSP_20230619053300_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = new Map([
  ['0820433', ['古河市長谷町38-18', '古河市役所古河庁舎']],
  ['0820434', ['古河市下大野2248', '古河市役所総和庁舎']],
  ['0821034', ['下妻市鬼怒230', '千代川公民館']],
  ['0821237', ['常陸太田市大中町1653', '常陸太田市役所里美支所']],
  ['0821931', ['牛久市中央3-15-1', '牛久市役所']],
  ['0822537', ['常陸大宮市山方660', '常陸大宮市役所山方支所']],
  ['0822538', ['常陸大宮市野口3195', '常陸大宮市役所御前山支所']],
  ['0822835', ['坂東市山2730', '坂東市役所猿島庁舎']],
  ['0823033', ['かすみがうら市上土田461', 'かすみがうら市千代田庁舎']],
  ['0823133', ['桜川市岩瀬64-2', '桜川市防災倉庫']],
  ['0823334', ['行方市山田2564-10', '行方市役所北浦庁舎']],
  ['0823533', ['つくばみらい市福田195', 'つくばみらい市役所伊奈庁舎']],
  ['0823633', ['小美玉市小川4-11', '小美玉市役所小川総合支所']],
  ['0831037', ['東茨城郡城里町徳蔵637', '城里町旧七会支所']],
  ['0844331', ['稲敷郡阿見町中央1-1-1', '阿見町役場']],
  ['1122132', ['草加市中央1-1-8', '草加市役所第二庁舎']],
  ['1122431', ['戸田市上戸田1-18-1', '戸田市役所']],
  ['1136532', ['秩父郡小鹿野町小鹿野89', '小鹿野町役場']],
  ['1138334', ['児玉郡神川町大字下阿久原1088', '神川町神泉総合支所']],
  ['1321451', ['国分寺市泉町2-2-3', '国分寺消防署']],
  ['1410141', ['横浜市鶴見区鶴見中央3-28-1', '鶴見土木事務所']],
  ['1410340', ['横浜市西区みなとみらい1-1-1', '臨港パーク']],
  ['1436230', ['足柄上郡大井町金子1995', '大井町役場']],
  ['1820131', ['福井市美山町7-1', '福井市役所美山庁舎']],
  ['1820137', ['福井市茱崎町1-68', '越廼公民館']],
  ['1936833', ['南巨摩郡富士川町天神中条1134', '富士川町役場']],
  ['2040733', ['下伊那郡阿智村清内路762-1', '阿智村清内路振興室']],
  ['2940231', ['高市郡明日香村大字橘21', '明日香村役場']],
  ['3220343', ['出雲市斐川町上庄原1760-1', '出雲市役所 まめなが一番館']],
  ['4120332', ['鳥栖市宿町1118', '鳥栖市役所']],
  ['4420545', ['佐伯市本匠大字笠掛2番地5', '本匠振興局']],
]);

for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update) continue;
  const isLegacySeinaiji = station.code === '2040733'
    && station.publishedAddressJa === null
    && station.facilityNameJa === '旧清内路振興室'
    && station.placementPrecision === 'municipality_or_ward';
  const isUnplaced = station.publishedAddressJa === null
    && station.facilityNameJa === null
    && station.placementPrecision === 'municipality_or_ward';
  if (!isLegacySeinaiji && !isUnplaced) {
    throw new Error(`Refusing unexpected pre-update state for ${station.code}`);
  }
  station.publishedAddressJa = update[0];
  station.facilityNameJa = update[1];
  station.metadataStatus = 'Official JMA July 2023 station-code notice and published address';
  station.sourceUrls.push(source);
  station.note = 'JMA’s July 2023 current station-code notice directly pairs this station name with the published host facility and Japanese address.';
  station.placementPrecision = 'exact_address';
  updates.delete(station.code);
}
if (updates.size) throw new Error(`Missing expected stations: ${[...updates.keys()].join(', ')}`);
if (data.coverage.exactPlacementAddressUpdates !== 2026 || data.coverage.localityPlacementRecords !== 1661) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2057;
data.coverage.localityPlacementRecords = 1630;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
