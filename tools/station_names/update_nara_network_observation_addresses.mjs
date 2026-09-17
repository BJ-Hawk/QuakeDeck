import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const source = 'https://www.city.ikoma.lg.jp/cmsfiles/contents/0000005/5554/03siryoushuu.pdf';

const updates = [
  ['2920131', '奈良市月ヶ瀬尾山2845', '月ヶ瀬行政センター'],
  ['2920133', '奈良市二条大路南1-1-1', '奈良市役所'],
  ['2920134', '奈良市都祁白石町1026-1', '都祁行政センター'],
  ['2920231', '大和高田市大字大中100-1', '大和高田市役所'],
  ['2920330', '大和郡山市北郡山町248-4', '大和郡山市役所'],
  ['2920431', '天理市川原城町605', '天理市役所'],
  ['2920531', '橿原市八木町1-1-18', '橿原市役所'],
  ['2920632', '桜井市大字粟殿432-1', '桜井市役所'],
  ['2920732', '五條市大塔町辻堂41', '大塔支所'],
  ['2920930', '生駒市東新町8-38', '生駒市役所'],
  ['2921031', '香芝市本町1397', '香芝市役所'],
  ['2921130', '葛城市柿本166', '葛城市役所新庄庁舎'],
  ['2921231', '宇陀市菟田野区松井502', '菟田野地域事務所'],
  ['2921232', '宇陀市榛原区下井足17-3', '宇陀市役所'],
  ['2921233', '宇陀市室生区大野1641', '室生地域事務所'],
  ['2921234', '宇陀市大宇陀区迫間25', '大宇陀地域事務所'],
  ['2932231', '山辺郡山添村大字大西151', '山添村役場'],
  ['2934230', '生駒郡平群町吉新1-1-1', '平群町役場'],
  ['2934330', '生駒郡三郷町勢野西1-1-1', '三郷町役場'],
  ['2934430', '生駒郡斑鳩町法隆寺西3-7-12', '斑鳩町役場'],
  ['2934530', '生駒郡安堵町大字東安堵958', '安堵町役場'],
  ['2936131', '磯城郡川西町大字結崎28-1', '川西町役場'],
  ['2936231', '磯城郡三宅町大字伴堂689', '三宅町役場'],
  ['2938530', '宇陀郡曽爾村大字今井495-1', '曽爾村役場'],
  ['2938630', '宇陀郡御杖村大字菅野368', '御杖村役場'],
  ['2940131', '高市郡高取町大字観覚寺990-1', '高取町役場'],
  ['2942430', '北葛城郡上牧町大字上牧3350', '上牧町役場'],
  ['2942531', '北葛城郡王寺町王寺2-1-23', '王寺町役場'],
  ['2942631', '北葛城郡広陵町大字南郷583-1', '広陵町役場'],
  ['2942730', '北葛城郡河合町池部1-1-1', '河合町役場'],
  ['2944131', '吉野郡吉野町大字上市80-1', '吉野町役場'],
  ['2944330', '吉野郡下市町大字下市1960', '下市町役場'],
  ['2944432', '吉野郡黒滝村大字寺戸77', '黒滝村役場'],
  ['2944631', '吉野郡天川村大字沢谷60', '天川村役場'],
  ['2944730', '吉野郡野迫川村大字北股84', '野迫川村役場'],
  ['2944932', '吉野郡十津川村大字小原225-1', '十津川村役場'],
  ['2945131', '吉野郡上北山村大字河合330', '上北山村役場'],
  ['2945230', '吉野郡川上村大字迫1355-7', '川上村役場'],
  ['2945332', '吉野郡東吉野村大字小川99', '東吉野村役場'],
];

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
for (const [code] of updates) {
  const station = parsed.stations.find((candidate) => candidate.code === code);
  if (!station || station.placementPrecision !== 'municipality_or_ward' || station.publishedAddressJa !== null || station.facilityNameJa !== null) {
    throw new Error(`Unexpected starting state for ${code}`);
  }
}
console.log(`Nara network address update: ${updates.length} directly documented stations.`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded update.');
  process.exit(0);
}

let revisedRaw = raw;
for (const [code, address, facility] of updates) {
  const startMarker = Buffer.from(`    {\n      "code": "${code}",`, 'utf8');
  const start = revisedRaw.indexOf(startMarker);
  if (start < 0) throw new Error(`Cannot find record ${code}`);
  const end = revisedRaw.indexOf(Buffer.from('\n    },', 'utf8'), start);
  if (end < 0) throw new Error(`Cannot find end of record ${code}`);
  const record = revisedRaw.subarray(start, end).toString('utf8');
  if (record.includes('\uFFFD')) throw new Error(`Refusing to rewrite invalid UTF-8 in ${code}`);
  for (const expected of [
    '      "publishedAddressJa": null,',
    '      "facilityNameJa": null,',
    '      "placementPrecision": "municipality_or_ward"',
  ]) {
    if (!record.includes(expected)) throw new Error(`Unexpected record content for ${code}`);
  }
  const note = 'Ikoma City’s official disaster-plan appendix directly lists this Nara Prefecture seismic-information-network observation label, host facility, and published address.';
  let revised = record
    .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(address)},`)
    .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(facility)},`)
    .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official prefectural seismic-network placement and address",')
    .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(note)},\n`)
    .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
  if (!revised.includes(source)) {
    revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(source)},`);
  }
  revisedRaw = Buffer.concat([revisedRaw.subarray(0, start), Buffer.from(revised, 'utf8'), revisedRaw.subarray(end)]);
}
fs.writeFileSync(inputPath, revisedRaw);
JSON.parse(fs.readFileSync(inputPath, 'utf8'));
console.log(`Applied Nara network address update: ${updates.length} stations.`);
