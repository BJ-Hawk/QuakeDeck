import { readFileSync, writeFileSync } from 'node:fs';

const SOURCE_URL = 'https://www.pref.okayama.jp/uploaded/life/766028_9625698_misc.pdf';
const SOURCE = [
  {
    "code": "3310131",
    "address": "岡山市北区御津金川1020",
    "facility": "御津支所"
  },
  {
    "code": "3310430",
    "address": "岡山市南区片岡207",
    "facility": "灘崎支所"
  },
  {
    "code": "3310132",
    "address": "岡山市北区建部町福渡489",
    "facility": "建部支所"
  },
  {
    "code": "3310330",
    "address": "岡山市東区瀬戸町瀬戸45",
    "facility": "瀬戸支所"
  },
  {
    "code": "3310332",
    "address": "岡山市東区西大寺南1-2-4",
    "facility": "東区役所"
  },
  {
    "code": "3310431",
    "address": "岡山市南区浦安南町640",
    "facility": "岡南飛行場"
  },
  {
    "code": "3310231",
    "address": "岡山市中区浜3-7-15",
    "facility": "中区役所"
  },
  {
    "code": "3320230",
    "address": "倉敷市白楽町162-5",
    "facility": "倉敷市消防局"
  },
  {
    "code": "3320233",
    "address": "倉敷市児島小川町3681-3",
    "facility": "児島支所"
  },
  {
    "code": "3320234",
    "address": "倉敷市水島北幸町103",
    "facility": "水島支所"
  },
  {
    "code": "3320235",
    "address": "倉敷市玉島阿賀崎1-1-1",
    "facility": "玉島支所"
  },
  {
    "code": "3320232",
    "address": "倉敷市真備町箭田1141-1",
    "facility": "真備支所"
  },
  {
    "code": "3320236",
    "address": "倉敷市船穂町船穂1697",
    "facility": "船穂公民館"
  },
  {
    "code": "3320330",
    "address": "津山市山北520",
    "facility": "津山市役所"
  },
  {
    "code": "3320337",
    "address": "津山市加茂町塔中104",
    "facility": "加茂支所"
  },
  {
    "code": "3320336",
    "address": "津山市阿波1209-4",
    "facility": "阿波支所"
  },
  {
    "code": "3320333",
    "address": "津山市新野東567",
    "facility": "勝北支所"
  },
  {
    "code": "3320334",
    "address": "津山市中北下1300",
    "facility": "久米支所"
  },
  {
    "code": "3320530",
    "address": "笠岡市中央町1-1",
    "facility": "笠岡市役所"
  },
  {
    "code": "3320732",
    "address": "井原市井原町311-1",
    "facility": "井原市役所"
  },
  {
    "code": "3320733",
    "address": "井原市美星町三山1055",
    "facility": "美星支所"
  },
  {
    "code": "3320734",
    "address": "井原市芳井町吉井253-1",
    "facility": "芳井支所"
  },
  {
    "code": "3320834",
    "address": "総社市地頭片山17-1",
    "facility": "山手出張所"
  },
  {
    "code": "3320835",
    "address": "総社市清音軽部1135",
    "facility": "清音出張所"
  },
  {
    "code": "3320936",
    "address": "高梁市松原通2043",
    "facility": "高梁市役所"
  },
  {
    "code": "3320935",
    "address": "高梁市有漢町有漢3387",
    "facility": "有漢地域局"
  },
  {
    "code": "3320937",
    "address": "高梁市成羽町下原606",
    "facility": "成羽複合施設"
  },
  {
    "code": "3320938",
    "address": "高梁市川上町地頭1819-1",
    "facility": "川上地域局"
  },
  {
    "code": "3320934",
    "address": "高梁市備中町布賀29-2",
    "facility": "備中地域局"
  },
  {
    "code": "3321035",
    "address": "新見市大佐小阪部1469-1",
    "facility": "大佐支局"
  },
  {
    "code": "3321032",
    "address": "新見市神郷下神代3936",
    "facility": "神郷支局"
  },
  {
    "code": "3321033",
    "address": "新見市哲多町本郷246-4",
    "facility": "哲多支局"
  },
  {
    "code": "3321034",
    "address": "新見市哲西町矢田3604",
    "facility": "哲西支局"
  },
  {
    "code": "3321036",
    "address": "新見市千屋実1435-7",
    "facility": "千屋市民センター"
  },
  {
    "code": "3321134",
    "address": "備前市東片上126",
    "facility": "備前市役所"
  },
  {
    "code": "3321131",
    "address": "備前市日生町日生630",
    "facility": "日生総合支所"
  },
  {
    "code": "3321133",
    "address": "備前市吉永町吉永中878",
    "facility": "吉永総合支所"
  },
  {
    "code": "3321232",
    "address": "瀬戸内市邑久町尾張300-1",
    "facility": "瀬戸内市役所"
  },
  {
    "code": "3321231",
    "address": "瀬戸内市牛窓町牛窓4911",
    "facility": "牛窓支所"
  },
  {
    "code": "3321234",
    "address": "瀬戸内市長船町土師291",
    "facility": "長船支所"
  },
  {
    "code": "3321330",
    "address": "赤磐市町苅田516",
    "facility": "赤坂支所"
  },
  {
    "code": "3321333",
    "address": "赤磐市松木623",
    "facility": "熊山支所"
  },
  {
    "code": "3321332",
    "address": "赤磐市周匝136",
    "facility": "吉井支所"
  },
  {
    "code": "3321431",
    "address": "真庭市勝山53-1",
    "facility": "真庭市立中央図書館"
  },
  {
    "code": "3321430",
    "address": "真庭市下呰部248",
    "facility": "北房振興局"
  },
  {
    "code": "3321443",
    "address": "真庭市落合垂水618",
    "facility": "落合地域総合センター"
  },
  {
    "code": "3321433",
    "address": "真庭市豊栄1515",
    "facility": "湯原振興局"
  },
  {
    "code": "3321442",
    "address": "真庭市久世2927-2",
    "facility": "真庭市役所"
  },
  {
    "code": "3321440",
    "address": "真庭市美甘4134",
    "facility": "美甘振興局"
  },
  {
    "code": "3321439",
    "address": "真庭市蒜山下福田305",
    "facility": "蒜山振興局"
  },
  {
    "code": "3321438",
    "address": "真庭市蒜山下和1802",
    "facility": "中和出張所"
  },
  {
    "code": "3321441",
    "address": "真庭市蒜山上福田425",
    "facility": "川上出張所"
  },
  {
    "code": "3321530",
    "address": "美作市真加部1616",
    "facility": "勝田総合支所"
  },
  {
    "code": "3321531",
    "address": "美作市古町1709",
    "facility": "大原総合支所"
  },
  {
    "code": "3321537",
    "address": "美作市太田152-1",
    "facility": "東粟倉総合支所"
  },
  {
    "code": "3321536",
    "address": "美作市江見945",
    "facility": "作東総合支所"
  },
  {
    "code": "3321535",
    "address": "美作市福本810-2",
    "facility": "英田総合支所"
  },
  {
    "code": "3321633",
    "address": "浅口市鴨方町六条院中3050",
    "facility": "浅口市役所"
  },
  {
    "code": "3321632",
    "address": "浅口市寄島町16010",
    "facility": "寄島総合支所"
  },
  {
    "code": "3321634",
    "address": "浅口市金光町占見新田751",
    "facility": "金光総合支所"
  },
  {
    "code": "3334630",
    "address": "和気郡和気町尺所555",
    "facility": "和気町役場"
  },
  {
    "code": "3334632",
    "address": "和気郡和気町矢田305",
    "facility": "佐伯庁舎"
  },
  {
    "code": "3342330",
    "address": "都窪郡早島町前潟360-1",
    "facility": "早島町役場"
  },
  {
    "code": "3344530",
    "address": "浅口郡里庄町里見1107-2",
    "facility": "里庄町役場"
  },
  {
    "code": "3346130",
    "address": "小田郡矢掛町矢掛3018",
    "facility": "矢掛町役場"
  },
  {
    "code": "3360635",
    "address": "苫田郡鏡野町竹田660",
    "facility": "鏡野町役場"
  },
  {
    "code": "3360634",
    "address": "苫田郡鏡野町富西谷125-1",
    "facility": "富振興センター"
  },
  {
    "code": "3360632",
    "address": "苫田郡鏡野町井坂495",
    "facility": "奥津振興センター"
  },
  {
    "code": "3362230",
    "address": "勝田郡勝央町勝間田201",
    "facility": "勝央町役場"
  },
  {
    "code": "3362330",
    "address": "勝田郡奈義町豊沢306-1",
    "facility": "奈義町役場"
  },
  {
    "code": "3364331",
    "address": "英田郡西粟倉村大字影石33",
    "facility": "西粟倉村役場"
  },
  {
    "code": "3366332",
    "address": "久米郡久米南町下弓削502-1",
    "facility": "久米南町役場"
  },
  {
    "code": "3366634",
    "address": "久米郡美咲町原田1735",
    "facility": "美咲町役場"
  },
  {
    "code": "3366633",
    "address": "久米郡美咲町久木200-8",
    "facility": "柵原総合支所"
  },
  {
    "code": "3368131",
    "address": "加賀郡吉備中央町豊野1-2",
    "facility": "賀陽庁舎"
  },
  {
    "code": "3368130",
    "address": "加賀郡吉備中央町下加茂1073-1",
    "facility": "加茂川庁舎"
  }
];
const catalog = JSON.parse(readFileSync('outputs/station-name-audit/station_metadata_sources.json', 'utf8'));
const stations = new Map(catalog.stations.map((station) => [station.code, station]));

if (process.argv.includes('--apply')) {
  for (const source of SOURCE) {
    const station = stations.get(source.code);
    if (!station || station.prefectureJa !== '岡山県' || station.publishedAddressJa) throw new Error('Refusing unexpected update: ' + source.code);
    station.publishedAddressJa = source.address;
    station.facilityNameJa = source.facility;
    station.placementPrecision = 'exact_address';
    delete station.placementLocalityJa;
    station.metadataStatus = 'Okayama Prefecture seismic-intensity system list (2021)';
    station.sourceUrls.push(SOURCE_URL);
    const note = 'Exact placement address and facility sourced from Okayama Prefecture seismic-intensity system list (2021).';
    station.note = station.note ? station.note + ' ' + note : note;
  }
  catalog.coverage.publishedAddresses += SOURCE.length;
  catalog.coverage.exactPlacementAddressUpdates += SOURCE.length;
  catalog.coverage.localityPlacementRecords -= SOURCE.length;
  writeFileSync('outputs/station-name-audit/station_metadata_sources.json', JSON.stringify(catalog, null, 2) + '\n');
}
console.log(JSON.stringify({ exactAddressUpdates: SOURCE.length, codes: SOURCE.map((source) => source.code) }));

