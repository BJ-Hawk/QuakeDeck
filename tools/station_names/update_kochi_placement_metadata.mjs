import { readFileSync, writeFileSync } from 'node:fs';

const DATA = 'outputs/station-name-audit/station_metadata_sources.json';
const SOURCE_URL = 'https://www.pref.kochi.lg.jp/doc/2026022700061/file_contents/04shiyousyo.pdf';
const SOURCE_NOTE = 'Verified against Kochi Prefecture’s FY2026 seismic-intensity network maintenance specification.';

// Exact facility/address pairs transcribed from the prefecture’s station table.
const ROWS = [
  ['3920430','南国市役所','南国市大桶甲2301'], ['3920930','土佐清水市役所','土佐清水市天神町11-2'],
  ['3921032','幡多土木事務所','四万十市古津4-61'], ['3921033','四万十市西土佐総合支所','四万十市西土佐江川崎2445-2'],
  ['3921138','香南市役所','香南市野市町西野2706'], ['3921137','香南市赤岡庁舎','香南市赤岡町325-1'],
  ['3921135','香南市香我美庁舎','香南市香我美町下分646'], ['3921136','香南市夜須庁舎','香南市夜須町坪井219'],
  ['3921139','香南市吉川庁舎','香南市吉川村吉原95'], ['3921233','香美市香北支所','香美市香北町美良布1097'],
  ['3921234','香美市物部支所','香美市物部町大栃1641'], ['3930230','奈半利町役場','安芸郡奈半利町乙1659-1'],
  ['3930330','田野町役場','安芸郡田野町1828-5'], ['3930433','安田町役場','安芸郡安田町安田1850'],
  ['3930531','北川村役場','安芸郡北川村野友甲1530'], ['3930630','馬路村役場','安芸郡馬路村大字馬路443'],
  ['3930731','芸西村役場','安芸郡芸西村和食甲1262'], ['3934131','嶺北広域行政事務所組合消防本部','長岡郡本山町本山995'],
  ['3934430','大豊町役場（旧庁舎敷地）','長岡郡大豊町高須231'], ['3936331','土佐町役場','土佐郡土佐町土居194'],
  ['3936431','大川村役場','土佐郡大川村小松27-1'], ['3938635','いの町役場','吾川郡いの町1700-1'],
  ['3938636','いの町本川総合支所','吾川郡いの町長沢123-12'], ['3938633','いの町吾北総合支所','吾川郡いの町上八川甲1934'],
  ['3938733','仁淀川町役場','吾川郡仁淀川町大崎200'], ['3938730','仁淀川町池川総合支所','吾川郡仁淀川町土居甲916-3'],
  ['3938732','仁淀川町仁淀総合支所','吾川郡仁淀川町森2571'], ['3940132','中土佐町役場','高岡郡中土佐町久礼6663-1'],
  ['3940131','中土佐町大野見庁舎','高岡郡中土佐町大野見吉野12'], ['3940230','佐川町役場','高岡郡佐川町甲1650-2'],
  ['3940330','越知町役場','高岡郡越知町越知甲1970'], ['3940532','梼原町役場','高岡郡梼原町梼原1444-1'],
  ['3941032','日高村役場','高岡郡日高村本郷61-1'], ['3941132','津野町役場','高岡郡津野町永野225-1'],
  ['3941131','津野町役場西庁舎','高岡郡津野町力石2870'], ['3941234','四万十町役場','高岡郡四万十町琴平町16-17'],
  ['3941233','四万十町十和総合支所','高岡郡四万十町十川151-1'], ['3942430','大月町役場','幡多郡大月町弘見2230'],
  ['3942730','三原村役場','幡多郡三原村来栖野346'], ['3920520','土佐市消防本部','土佐市蓮池978-1'],
  ['3920820','宿毛市役所','宿毛市桜町2-1'], ['3930120','東洋町役場','安芸郡東洋町生見758-3'],
  ['3942820','黒潮町佐賀支所','幡多郡黒潮町佐賀1092-1'], ['3941220','四万十町大正総合支所','高岡郡四万十町大正380']
];

const apply = process.argv.includes('--apply');
const data = JSON.parse(readFileSync(DATA, 'utf8'));
const byCode = new Map(data.stations.map(station => [station.code, station]));
const updates = [];
for (const [code, facility, address] of ROWS) {
  const station = byCode.get(code);
  if (!station) throw new Error(`Unknown station ${code}`);
  if (station.prefectureJa !== '高知県') throw new Error(`Wrong prefecture for ${code}`);
  if (station.publishedAddressJa) throw new Error(`Refusing to replace existing address for ${code}`);
  updates.push({ station, facility, address });
}
if (!apply) {
  console.log(JSON.stringify({ candidates: updates.map(({station,facility,address}) => ({code: station.code, nameJa: station.nameJa, facility, address})) }, null, 2));
  process.exit(0);
}
for (const { station, facility, address } of updates) {
  station.publishedAddressJa = address;
  station.facilityNameJa = facility;
  station.placementPrecision = 'exact_address';
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls || []), SOURCE_URL])];
  station.metadataStatus = 'source_verified';
  station.note = SOURCE_NOTE;
}
data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;
writeFileSync(DATA, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({ updated: updates.map(({station}) => station.code), count: updates.length }, null, 2));
