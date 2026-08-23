import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = new Map([
  ['4060430', {
    address: '福岡県田川郡糸田町1975番地1',
    facilityJa: '糸田町役場', facilityEn: 'Itoda Town Office',
    url: 'https://www.town.itoda.lg.jp/administration/information'
  }],
  ['1522632', {
    address: '新潟県南魚沼市塩沢1370番地1',
    facilityJa: '南魚沼市役所塩沢庁舎', facilityEn: 'Minamiuonuma City Hall Shiozawa Office',
    url: 'https://www.city.minamiuonuma.niigata.jp/docs/2504.html'
  }],
  ['1510331', {
    address: '新潟県新潟市中央区学校町通1番町602番地1',
    facilityJa: '新潟市役所', facilityEn: 'Niigata City Hall',
    url: 'https://www.city.niigata.lg.jp/shisetsu/tyousya/access.html'
  }],
  ['1330740', {
    address: '東京都西多摩郡檜原村467-1',
    facilityJa: '檜原村役場', facilityEn: 'Hinohara Village Office',
    url: 'https://www.vill.hinohara.tokyo.jp/category/2-3-2-0-0-0-0-0-0-0.html'
  }],
  ['1336431', {
    address: '東京都神津島村904番地',
    facilityJa: '神津島村役場', facilityEn: 'Kozushima Village Office',
    url: 'https://www.vill.kouzushima.tokyo.jp/about/'
  }],
  ['2120730', {
    address: '岐阜県美濃市1350番地',
    facilityJa: '美濃市役所', facilityEn: 'Mino City Hall',
    url: 'https://www.city.mino.gifu.jp/docs/2196.html'
  }],
  ['2136131', {
    address: '岐阜県不破郡垂井町宮代2957-11',
    facilityJa: '垂井町役場', facilityEn: 'Tarui Town Office',
    url: 'https://www.town.tarui.lg.jp/map/4809.html'
  }],
  ['0348430', {
    address: '岩手県下閉伊郡田野畑村田野畑143-1',
    facilityJa: '田野畑村役場', facilityEn: 'Tanohata Village Office',
    url: 'https://www.vill.tanohata.iwate.jp/'
  }],
  ['0330231', {
    address: '岩手県岩手郡葛巻町葛巻16-1-1',
    facilityJa: '葛巻町役場', facilityEn: 'Kuzumaki Town Office',
    url: 'https://www.town.kuzumaki.lg.jp/soshiki/kuzumaki/more.p5.html'
  }],
  ['0744734', {
    address: '福島県大沼郡会津美里町鶴野辺字広町740番地',
    facilityJa: '会津美里町役場新鶴庁舎', facilityEn: 'Aizumisato Town Hall Niitsuru Office',
    url: 'https://www.town.aizumisato.fukushima.jp/soshiki/1007/6/kyoudo/4988.html'
  }],
  ['0820232', {
    address: '茨城県日立市助川町1丁目1番1号',
    facilityJa: '日立市役所', facilityEn: 'Hitachi City Hall',
    url: 'https://www.city.hitachi.lg.jp/shisetsu/shikanrenshisetsu/1005730/1005731.html'
  }],
  ['0820831', {
    address: '茨城県龍ケ崎市3710番地',
    facilityJa: '龍ケ崎市役所', facilityEn: 'Ryugasaki City Hall',
    url: 'https://www.city.ryugasaki.ibaraki.jp/mobile/annai/index.html'
  }],
  ['0822834', {
    address: '茨城県坂東市岩井4365番地',
    facilityJa: '坂東市役所', facilityEn: 'Bando City Hall',
    url: 'https://www.city.bando.lg.jp/page/page000157.html'
  }],
  ['0822934', {
    address: '茨城県稲敷市犬塚1570番地1',
    facilityJa: '稲敷市役所', facilityEn: 'Inashiki City Hall',
    url: 'https://www.city.inashiki.lg.jp/page/page001917.html'
  }],
  ['0920636', {
    address: '栃木県日光市鬼怒川温泉大原1406番地2',
    facilityJa: '日光市藤原庁舎', facilityEn: 'Nikko City Hall Fujiwara Office',
    url: 'https://www.city.nikko.lg.jp/city_nikko/shisetsu/1/5932.html'
  }],
  ['0921333', {
    address: '栃木県那須塩原市中塩原1番地2',
    facilityJa: '那須塩原市塩原庁舎', facilityEn: 'Nasushiobara City Hall Shiobara Office',
    url: 'https://www.city.nasushiobara.tochigi.jp/soshikikarasagasu/ssisho/3522.html'
  }],
  ['0921531', {
    address: '栃木県那須烏山市中央1丁目1番1号',
    facilityJa: '那須烏山市役所烏山庁舎', facilityEn: 'Nasukarasuyama City Hall Karasuyama Office',
    url: 'https://www.city.nasukarasuyama.lg.jp/city-administration/organization/counter-guide/page001081.html'
  }],
  ['1221133', {
    address: '千葉県成田市花崎町760番地',
    facilityJa: '成田市役所', facilityEn: 'Narita City Hall',
    url: 'https://www.city.narita.chiba.jp/shisei/page276000.html'
  }],
  ['1223630', {
    address: '千葉県香取市佐原ロ2127番地',
    facilityJa: '香取市役所', facilityEn: 'Katori City Hall',
    url: 'https://www.city.katori.lg.jp/government/profile/index.html'
  }],
  ['1920941', {
    address: '山梨県北杜市須玉町大豆生田961-1',
    facilityJa: '北杜市役所', facilityEn: 'Hokuto City Hall',
    url: 'https://www.city.hokuto.yamanashi.jp/docs/1195.html'
  }],
  ['1921333', {
    address: '山梨県甲州市塩山上於曽1085番地1',
    facilityJa: '甲州市役所', facilityEn: 'Koshu City Hall',
    url: 'https://www.city.koshu.yamanashi.jp/category/bunya/koshu_gaiyo/choshaannai/'
  }],
  ['1934631', {
    address: '山梨県西八代郡市川三郷町市川大門1790-3',
    facilityJa: '市川三郷町役場', facilityEn: 'Ichikawamisato Town Office',
    url: 'https://www.town.ichikawamisato.yamanashi.jp/40administration/index.html'
  }],
  ['1936532', {
    address: '山梨県南巨摩郡身延町切石350',
    facilityJa: '身延町役場', facilityEn: 'Minobu Town Office',
    url: 'https://www.town.minobu.lg.jp/page/1269.html'
  }],
  ['1920631', {
    address: '山梨県大月市大月2丁目6番20号',
    facilityJa: '大月市役所', facilityEn: 'Otsuki City Hall',
    url: 'https://www.city.otsuki.yamanashi.jp/access/'
  }],
  ['1942932', {
    address: '山梨県南都留郡鳴沢村1575',
    facilityJa: '鳴沢村役場', facilityEn: 'Narusawa Village Office',
    url: 'https://www.vill.narusawa.yamanashi.jp/gyosei/muragyosei/shisetsu/yakuba/index.html'
  }],
  ['1943036', {
    address: '山梨県南都留郡富士河口湖町船津1700番地',
    facilityJa: '富士河口湖町役場', facilityEn: 'Fujikawaguchiko Town Office',
    url: 'https://www.town.fujikawaguchiko.yamanashi.jp/ka/list.php?ka_id=3'
  }],
  ['2521130', {
    address: '滋賀県湖南市石部中央一丁目1番1号',
    facilityJa: '湖南市役所西庁舎', facilityEn: 'Konan City Hall West Office',
    url: 'https://www.city.shiga-konan.lg.jp/soshiki/somu/kanzaikeiyaku/4/2/2/2324.html'
  }],
  ['2521131', {
    address: '滋賀県湖南市中央一丁目1番地',
    facilityJa: '湖南市役所東庁舎', facilityEn: 'Konan City Hall East Office',
    url: 'https://www.city.shiga-konan.lg.jp/soshiki/somu/kanzaikeiyaku/4/2/2/2324.html'
  }],
  ['2714131', {
    address: '大阪府堺市堺区南瓦町3番1号',
    facilityJa: '堺区役所', facilityEn: 'Sakai Ward Office',
    url: 'https://www.city.sakai.lg.jp/shisei/gaiyo/annai/gyoseikiko/sakai/index.html'
  }],
  ['2720230', {
    address: '大阪府岸和田市岸城町7番1号',
    facilityJa: '岸和田市役所', facilityEn: 'Kishiwada City Hall',
    url: 'https://www.city.kishiwada.lg.jp/life/sub/5/39/'
  }],
  ['2920830', {
    address: '奈良県御所市1番地の3',
    facilityJa: '御所市役所', facilityEn: 'Gose City Hall',
    url: 'https://www.city.gose.nara.jp/category/7-12-11-0-0-0-0-0-0-0.html'
  }],
  ['2936331', {
    address: '奈良県磯城郡田原本町890番地1',
    facilityJa: '田原本町役場', facilityEn: 'Tawaramoto Town Office',
    url: 'https://www.town.tawaramoto.nara.jp/material/files/group/3/240422_25.pdf'
  }],
  ['3042230', {
    address: '和歌山県東牟婁郡太地町大字太地3767-1',
    facilityJa: '太地町役場', facilityEn: 'Taiji Town Office',
    url: 'https://town.taiji.wakayama.jp/'
  }],
  ['3252731', {
    address: '島根県隠岐郡知夫村1065番地',
    facilityJa: '知夫村役場', facilityEn: 'Chibu Village Office',
    url: 'https://chibu-vill.note.jp/n/n64079088f82b'
  }],
  ['3430431', {
    address: '広島県安芸郡海田町南昭和町14番17号',
    facilityJa: '海田町役場', facilityEn: 'Kaita Town Office',
    url: 'https://www.town.kaita.lg.jp/soshiki/5/143783.html'
  }],
  ['3430731', {
    address: '広島県安芸郡熊野町中溝一丁目1番1号',
    facilityJa: '熊野町役場', facilityEn: 'Kumano Town Office',
    url: 'https://www.town.kumano.lg.jp/7/6/2/index.html'
  }],
  ['3430930', {
    address: '広島県安芸郡坂町平成ヶ浜一丁目1番1号',
    facilityJa: '坂町役場', facilityEn: 'Saka Town Office',
    url: 'https://www.town.saka.lg.jp/category/%E7%94%BA%E6%94%BF%E6%83%85%E5%A0%B1/%E5%9D%82%E7%94%BA%E3%81%AE%E3%81%94%E7%B4%B9%E4%BB%8B/%E5%9D%82%E7%94%BA%E5%BD%B9%E5%A0%B4%E3%81%B8%E3%81%AE%E3%82%A2%E3%82%AF%E3%82%BB%E3%82%B9/'
  }],
  ['0632132', {
    address: '山形県西村山郡河北町谷地戊81番地',
    facilityJa: '河北町役場', facilityEn: 'Kahoku Town Office',
    url: 'https://trip-yamagata-japan.com/barrier-free/detail/index/454'
  }],
  ['1921140', {
    address: '山梨県笛吹市石和町市部777番地',
    facilityJa: '笛吹市役所', facilityEn: 'Fuefuki City Hall',
    url: 'https://www.city.fuefuki.yamanashi.jp/documents/898/41ac01f670595.pdf'
  }],
  ['1921233', {
    address: '山梨県上野原市上野原3832',
    facilityJa: '上野原市役所', facilityEn: 'Uenohara City Hall',
    url: 'https://www.city.uenohara.yamanashi.jp/form/detail.php?lif_id=19314&sec_sec1=4'
  }],
  ['3358630', {
    address: '岡山県真庭郡新庄村2008-1',
    facilityJa: '新庄村役場', facilityEn: 'Shinjo Village Office',
    url: 'https://www.vill.shinjo.okayama.jp/index.php?id=3369'
  }],
  ['3736432', {
    address: '香川県香川郡直島町1122番地1',
    facilityJa: '直島町役場', facilityEn: 'Naoshima Town Office',
    url: 'https://www.town.naoshima.lg.jp/about/shisetsu/townhall.html'
  }],
  ['3738631', {
    address: '香川県綾歌郡宇多津町1881',
    facilityJa: '宇多津町役場', facilityEn: 'Utazu Town Office',
    url: 'https://www.town.utazu.lg.jp/uploaded/attachment/3616.pdf'
  }],
  ['3520144', {
    address: '山口県下関市南部町1番1号',
    facilityJa: '下関市役所', facilityEn: 'Shimonoseki City Hall',
    url: 'https://www.city.shimonoseki.lg.jp/map/siyakusyo1.html'
  }],
  ['4432231', {
    address: '大分県東国東郡姫島村1630番地の1',
    facilityJa: '姫島村役場', facilityEn: 'Himeshima Village Office',
    url: 'https://www.himeshima.jp/'
  }],
  ['4434130', {
    address: '大分県速見郡日出町2974番地1',
    facilityJa: '日出町役場', facilityEn: 'Hiji Town Office',
    url: 'https://www.town.hiji.lg.jp/gyoseijoho/shisetsuannai/hijimachiyakubachosha/index.html'
  }],
  ['4420543', {
    address: '大分県佐伯市中村南町1番1号',
    facilityJa: '佐伯市役所本庁舎', facilityEn: 'Saiki City Hall Main Office',
    url: 'https://www.city.saiki.oita.jp/kiji0032113/index.html'
  }],
  ['4520730', {
    address: '宮崎県串間市大字西方5550',
    facilityJa: '串間市役所', facilityEn: 'Kushima City Hall',
    url: 'https://www.city.kushima.lg.jp/main/info/cat3391/'
  }],
  ['4538331', {
    address: '宮崎県東諸県郡綾町大字南俣515番地',
    facilityJa: '綾町役場', facilityEn: 'Aya Town Office',
    url: 'https://www.town.aya.miyazaki.jp/soshiki/index-50.html'
  }],
  ['4730832', {
    address: '沖縄県国頭郡本部町字東5番地',
    facilityJa: '本部町役場', facilityEn: 'Motobu Town Office',
    url: 'https://www.town.motobu.okinawa.jp/'
  }],
  ['4735532', {
    address: '沖縄県島尻郡粟国村字東483番地',
    facilityJa: '粟国村役場', facilityEn: 'Aguni Village Office',
    url: 'https://www.vill.aguni.okinawa.jp/soshiki/index.html'
  }],
  ['4735930', {
    address: '沖縄県島尻郡伊平屋村字我喜屋251番地',
    facilityJa: '伊平屋村役場', facilityEn: 'Iheya Village Office',
    url: 'https://www1.g-reiki.net/iheya/reiki_honbun/q946RG00000001.html'
  }],
  ['0744733', {
    address: '福島県大沼郡会津美里町字北川原41',
    facilityJa: '会津美里町役場本郷庁舎', facilityEn: 'Aizumisato Town Office Hongo Branch',
    url: 'https://www.town.aizumisato.fukushima.jp/soshiki/1008/1/2_1/581.html'
  }],
  ['0744735', {
    address: '福島県大沼郡会津美里町字宮北3163番地',
    facilityJa: '会津美里町役場高田庁舎', facilityEn: 'Aizumisato Town Office Takada Branch',
    url: 'https://www.119-aizu.jp/aedmisato.htm'
  }],
  ['4735730', {
    address: '沖縄県島尻郡南大東村字南144番地1',
    facilityJa: '南大東村役場', facilityEn: 'Minamidaito Village Office',
    url: 'https://www.vill.minamidaito.okinawa.jp/life/sub/1/25/113/'
  }],
  ['4738230', {
    address: '沖縄県八重山郡与那国町字与那国129',
    facilityJa: '与那国町役場', facilityEn: 'Yonaguni Town Office',
    url: 'https://www.town.yonaguni.okinawa.jp/docs/2018080700015/'
  }]
]);

let changed = 0;
for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update || station.publishedAddressJa) continue;
  station.publishedAddressJa = update.address;
  station.facilityNameJa = update.facilityJa;
  station.facilityNameEn = update.facilityEn;
  station.metadataStatus = 'Official municipal facility placement';
  station.sourceUrls = [...new Set([...station.sourceUrls, update.url])];
  station.note = 'The official station label identifies this municipal facility; the municipality publishes its address.';
  station.placementPrecision = 'exact_address';
  changed += 1;
}
if (changed) {
  data.coverage.publishedAddresses += changed;
  data.coverage.exactPlacementAddressUpdates += changed;
  data.coverage.localityPlacementRecords -= changed;
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
}
console.log(`updated ${changed} station(s)`);
