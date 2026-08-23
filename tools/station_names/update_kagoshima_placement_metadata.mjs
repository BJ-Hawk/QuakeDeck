import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.kkj.go.jp/d/?A=a2Fnb3NoaW1hL2thZ29zaGltYV9wcmVmLzIwMjQvMjAyNDAzMDhfMDA2MjVfMDMucGRmCg%3D%3D&L=ja';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
// Code, facility, and address transcribed from the prefecture's 2024 maintenance table.
// Rows are included only when the station's published locality uniquely identifies the listed site.
const exact = [
['4620135','鹿児島市吉田支所','鹿児島市本城町１６９６'],['4620136','鹿児島市郡山支所','鹿児島市郡山町１４１'],['4620137','鹿児島市桜島支所','鹿児島市桜島藤野町１４３９'],['4620138','鹿児島市松元支所','鹿児島市上谷口町２８８３'],
['4620334','鹿屋市輝北総合支所','鹿屋市輝北町上百引３９１４のロ'],['4620335','鹿屋市吾平総合支所','鹿屋市吾平町麓３３１７'],['4620336','鹿屋市串良総合支所','鹿屋市串良町岡崎２０８１'],
['4620830','出水市桂島小学校','出水市桂島小学校敷地内'],['4620835','出水市高尾野支所','出水市高尾野町大久保７'],['4620836','出水市野田支所','出水市野田町下名７０３５'],['4621032','指宿市開聞支所','指宿市開聞十町２８６７'],
['4621533','薩摩川内市祁答院支所','薩摩川内市祁答院町下手６７'],['4621534','薩摩川内市里支所','薩摩川内市里町里１９２２'],['4621535','薩摩川内市鹿島支所','薩摩川内市鹿島町藺牟田１４５７の１０'],['4621536','薩摩川内市入来支所','薩摩川内市入来町浦之名３３'],['4621537','薩摩川内市東郷支所','薩摩川内市東郷町斧渕３６２'],['4621538','薩摩川内市樋脇支所','薩摩川内市樋脇町塔之原１１７３'],
['4621633','日置市東市来町','日置郡東市来町長里８７の１'],['4621634','日置市本所','日置市伊集院町郡１の１００'],['4621635','日置市吹上支所','日置市吹上町中原２８４７'],
['4621731','曽於市本所','曽於市末吉町二之方１９８０'],['4621733','曽於市財部支所','曽於市財部町南俣１１２７５'],
['4621834','霧島市溝辺支所','霧島市溝辺町有川３４１'],['4621836','霧島市霧島支所','霧島市霧島町田口８の４'],['4621837','霧島市福山牧之原支所','霧島市福山町福山５２９０－６１'],['4621838','霧島市牧園支所','霧島市牧園町宿窪田２６４７'],['4621931','いちき串木野市市来総合支所','いちき串木野市湊町１－１'],
['4622035','南さつま市大浦支所','南さつま市大浦町２０７１'],['4622038','南さつま市笠沙支所','南さつま市笠沙町片浦８０８'],['4622039','南さつま市金峰支所','南さつま市金峰町尾下１６５０'],['4622040','南さつま市坊津支所','南さつま市坊津町久志２４２２の１'],
['4622130','志布志市松山支所','志布志市松山町新橋２６８'],['4622131','志布志市本所','志布志市有明町野井倉１７５６'],['4622331','南九州市川辺総合支所','南九州市川辺町平山３２３４'],['4622332','南九州市役所','南九州市知覧町郡６２０４'],['4622431','伊佐市菱刈庁舎','伊佐市菱刈前目２１０６'],
['4622535','姶良市役所','姶良郡姶良町宮島町２５'],['4622536','加治木総合支所','姶良市加治木町本町２５３'],['4639230','さつま町鶴田支所','薩摩郡さつま町神子６６３の１'],['4639231','さつま町薩摩支所','薩摩郡さつま町求名１２８３７'],
['4640431','長島町獅子島コミュニティセンター','出水郡長島町獅子島コミュニティセンター敷地内'],['4640433','長島町長島支所','出水郡長島町指江７８７'],['4640434','長島町伊唐島コミュニティセンター','出水郡長島町伊唐島コミュニティセンター敷地内'],['4645232','湧水町栗野庁舎','姶良郡湧水町栗野木場２２２'],['4645233','湧水町吉松庁舎','姶良郡湧水町中津川６０３'],
['4648231','東串良町役場','肝属郡東串良町川西１５４３'],['4649031','錦江町役場','肝属郡錦江町城元９６３'],['4649132','南大隅町本庁','肝属郡南大隅町根占川北２２６'],['4649231','肝付町役場','肝属郡肝付町新富９８'],
['4622235','奄美市住用支所','奄美市住用町大字西中間１１１'],['4622236','奄美市役所','奄美市名瀬幸町２５の８'],['4652431','宇検村役場','大島郡宇検村湯湾９１５'],['4652731','龍郷町役場','大島郡龍郷町浦１１０'],['4652931','喜界町役場','大島郡喜界町大字湾１７４６'],['4653033','徳之島町役場','大島郡徳之島町亀津７２０３'],['4653131','天城町役場','大島郡天城町平土野２６９１－１'],['4653332','和泊町役場','大島郡和泊町和泊１０']
];
const facilityOnly = new Map([
  ['4620830', '鹿児島出水市'],
  ['4640431', '長島町'],
  ['4640434', '長島町']
]);
let changed = 0;
for (const [code, facility, address] of exact) {
  const station = data.stations.find(s => s.code === code);
  const isFacilityOnly = facilityOnly.has(code);
  if (!station || (!isFacilityOnly && station.publishedAddressJa)) continue;
  const wasExact = station.placementPrecision === 'exact_address';
  if (!isFacilityOnly) station.publishedAddressJa = address;
  else delete station.publishedAddressJa;
  station.facilityNameJa = facility;
  station.placementPrecision = isFacilityOnly ? 'municipality_or_ward' : 'exact_address';
  if (isFacilityOnly) station.placementLocalityJa = facilityOnly.get(code);
  else delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls || []), source])];
  station.metadataStatus = 'source_verified';
  station.note = 'Verified against Kagoshima Prefecture’s 2024 seismic-intensity network maintenance table.';
  if (!isFacilityOnly) changed++;
  else if (wasExact) changed--;
}
data.coverage.publishedAddresses += changed;
data.coverage.exactPlacementAddressUpdates += changed;
data.coverage.localityPlacementRecords -= changed;
fs.writeFileSync(path, JSON.stringify(data, null, 2) + '\n');
console.log(`updated ${changed} exact station placements`);
