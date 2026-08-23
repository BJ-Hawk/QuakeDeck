import { readFileSync, writeFileSync } from 'node:fs';

const DATA = 'outputs/station-name-audit/station_metadata_sources.json';
const SOURCE_URL = 'https://e-ppi.pref.tokushima.lg.jp/file/anken/360000/22270/4/06%20%E4%BB%A4%E5%92%8C%EF%BC%94%E5%B9%B4%E5%BA%A6%E5%BE%B3%E5%B3%B6%E7%9C%8C%E9%9C%87%E5%BA%A6%E6%83%85%E5%A0%B1%E3%83%8D%E3%83%83%E3%83%88%E3%83%AF%E3%83%BC%E3%82%AF%E3%82%B7%E3%82%B9%E3%83%86%E3%83%A0%E5%86%8D%E6%95%B4%E5%82%99%E6%A5%AD%E5%8B%99%EF%BC%88%E7%AC%AC5%E5%88%86%E5%89%B2%EF%BC%89%20%E4%BB%95%E6%A7%98%E6%9B%B8.pdf';
const NOTE = 'Verified against Tokushima Prefecture’s seismic-intensity network renewal specification.';
const ROWS = [
['3620131','徳島市消防局','徳島市新蔵町1-88'],['3620332','小松島消防本部','小松島市横須町1-1'],
['3620533','吉野川市川島庁舎','吉野川市川島町2421-1'],['3620534','吉野川市消防会館','吉野川市山川町翁喜台56番地2'],['3620535','吉野川市ふるさとセンター','吉野川市美郷字中筋194番地1'],
['3620621','阿波市市場コミュニティーセンター','阿波市市場町市場字上野段385-1'],['3620634','阿波市土成支所','阿波市土成町土成字丸山1-1'],['3620635','阿波市阿波支所','阿波市阿波町東原173'],['3620636','阿波市吉野支所','阿波市吉野町西条字大西60-1'],
['3620720','木屋平村中学校','美馬市木屋平字川井171-2'],['3620732','美馬町市民サービスセンター','美馬市美馬町字天神121'],['3620733','美馬市役所','美馬市穴吹町穴吹字九反地5'],
['3620820','三好立池田中学校','三好市池田町マチ2861-1'],['3620822','東祖谷中学校','三好市東祖谷下瀬12-1'],['3620834','三野老人福祉センター','三好市三野町芝生1036-1'],['3620836','三好市井川総合支所','三好市井川町辻73'],['3620838','三好市西祖谷総合支所','三好市西祖谷山村一宇343-2'],['3620839','三好市山城総合支所','三好市山城町大川持518-9'],
['3632130','佐那河内村役場','名東郡佐那河内村下字中辺71-1'],['3634132','石井中学校用地内','名西郡石井町高川原字高川原125-1'],['3634231','神山町役場','名西郡神山町神領字本野間100'],
['3640131','松茂町役場','板野郡松茂町広島字東裏30'],['3640231','北島町役場','板野郡北島町中村字上地23-1'],['3640331','藍住町役場','板野郡藍住町奥野字矢上前52-1'],['3640431','板野町民センター','板野郡板野町大寺亀山西169-5'],['3640531','上板町役場','板野郡上板町七條字経塚42'],
['3646821','つるぎ町就業構造改善センター','美馬郡つるぎ町貞光字宮下61'],['3646832','つるぎ町役場半田支所','美馬郡つるぎ町半田字木の内136-1'],['3646833','つるぎ町役場一宇支所','美馬郡つるぎ町一宇字赤松541-2'],['3648932','東みよし町三好庁舎','三好郡東みよし町昼間3673-1'],['3648933','東みよし町役場','三好郡東みよし町加茂3360'],
['3620432','阿南市那賀川支所','阿南市那賀川町苅屋323'],['3620434','阿南市羽ノ浦支所','阿南市羽ノ浦町中庄なかれ16-3'],['3630131','勝浦町役場','勝浦郡勝浦町大字久国字久保田3'],['3630220','旭農林公園','勝浦郡上勝町大字旭字蔭72'],
['3636820','那賀町木頭体育館','那賀郡那賀町木頭和無田字ソ子24'],['3636830','那賀町役場','那賀郡那賀町和食郷字南川104-1'],['3636836','木沢総合防災センター','那賀郡那賀町木頭字前田43-1'],['3636837','那賀町相生健康センター','那賀郡那賀町延野字王子原31-1'],['3636838','那賀町上那賀支所','那賀郡那賀町小浜151'],
['3638320','牟岐小学校','海部郡牟岐町大字中村字本村10-57'],['3638720','美波町由岐支所','海部郡美波町西の地字西地50-1'],['3638731','美波町役場','海部郡美波町奥河内字本村21'],
['3638833','海陽町役場','海部郡海陽町大里字上中須128'],['3638834','海陽町海部庁舎','海部郡海陽町奥浦字新町44'],['3638836','海陽町宍喰町民センター','海部郡海陽町宍喰浦字宍喰362']
];
const apply = process.argv.includes('--apply');
const data = JSON.parse(readFileSync(DATA, 'utf8'));
const byCode = new Map(data.stations.map(s => [s.code, s]));
const updates = ROWS.map(([code, facility, address]) => {
  const station = byCode.get(code);
  if (!station || station.prefectureJa !== '徳島県') throw new Error(`Invalid station ${code}`);
  if (station.publishedAddressJa) throw new Error(`Refusing to replace existing address for ${code}`);
  return {station, facility, address};
});
if (!apply) { console.log(JSON.stringify(updates.map(({station,facility,address}) => ({code:station.code,nameJa:station.nameJa,facility,address})), null, 2)); process.exit(0); }
for (const {station, facility, address} of updates) {
  station.publishedAddressJa = address; station.facilityNameJa = facility; station.placementPrecision = 'exact_address'; delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls || []), SOURCE_URL])]; station.metadataStatus = 'source_verified'; station.note = NOTE;
}
data.coverage.publishedAddresses += updates.length; data.coverage.exactPlacementAddressUpdates += updates.length; data.coverage.localityPlacementRecords -= updates.length;
writeFileSync(DATA, `${JSON.stringify(data, null, 2)}\n`, 'utf8'); console.log(JSON.stringify({count:updates.length,updated:updates.map(({station})=>station.code)},null,2));
