import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.pref.osaka.lg.jp/documents/105964/b2-0720shindonetwork.pdf';
// Osaka Prefecture's station-network sheet names the installed facility for each
// municipality/ward point.  Addresses are deliberately not inferred here.
const facilities = new Map([
  ['2710231', '大阪市都島消防署'], ['2710330', '大阪市福島消防署上福島出張所'],
  ['2710431', '大阪市此花区役所'], ['2710630', '大阪市西消防署'],
  ['2710730', '大阪市水上消防署'], ['2710831', '大阪市大正消防署泉尾出張所'], ['2710930', '大阪市天王寺消防署'],
  ['2711131', '大阪市浪速消防署'], ['2711331', '大阪府西淀川警察署'],
  ['2711430', '大阪市東淀川消防署井高野出張所'], ['2711530', '大阪市東成消防署中本出張所'],
  ['2711630', '大阪市生野消防署'], ['2711731', '大阪市旭消防署'],
  ['2711831', '大阪市城東消防署放出出張所'], ['2711930', '大阪市阿倍野消防署'],
  ['2712030', '大阪市住吉消防署'], ['2712130', '大阪市東住吉消防署杭全出張所'],
  ['2712230', '大阪市西成消防署'], ['2712330', '大阪市淀川消防署'],
  ['2712430', '大阪市鶴見消防署'], ['2712531', '大阪市住之江消防署'],
  ['2712630', '大阪市平野消防署'], ['2712730', '大阪市北消防署'],
  ['2712831', '大阪府庁'],
  ['2720330', '豊中市役所'], ['2720430', '池田市役所'], ['2720530', '吹田市消防本部南消防署'],
  ['2720931', '守口市役所'], ['2721031', '枚方市役所'], ['2721130', '茨木市消防本部'],
  ['2721230', '八尾市役所'], ['2721831', '大東四條畷消防組合消防本部'],
  ['2722031', '箕面市消防本部東分署'], ['2722131', '柏原市役所'], ['2722331', '門真市役所'],
  ['2722430', '摂津市役所'], ['2722731', '東大阪市役所'], ['2722930', '大東四條畷消防組合四條畷消防署'],
  ['2723030', '交野市役所'], ['2730130', '島本町消防本部'], ['2732130', '豊能町役場'], ['2732231', '能勢町役場'],
  ['2714132', '堺市役所'], ['2714330', '堺市東区役所'], ['2714431', '堺市西区役所'],
  ['2714531', '堺市南区役所'], ['2714631', '堺市北区役所'], ['2714730', '堺市消防局美原消防署'],
  ['2720631', '泉大津市役所'], ['2720831', '貝塚市役所'], ['2721332', '泉州南広域消防本部'],
  ['2721333', '泉佐野市役所'], ['2721430', '富田林市消防本部金剛分署'], ['2721630', '河内長野市役所'],
  ['2721730', '松原市役所'], ['2721932', '和泉市役所'], ['2722231', '羽曳野市役所'],
  ['2722530', '高石市役所'], ['2722630', '藤井寺市役所'], ['2722830', '泉州南消防組合泉南消防署'],
  ['2723130', '大阪狭山市役所'], ['2723230', '阪南市役所'], ['2734132', '忠岡町役場'],
  ['2736130', '泉州南消防組合熊取消防署'], ['2736230', '田尻町役場'], ['2736630', '岬町役場'],
  ['2738130', '太子町役場'], ['2738230', '河南町役場'], ['2738333', '千早赤阪村役場']
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let changed = 0;
for (const station of data.stations) {
  const facility = facilities.get(station.code);
  if (!facility || station.publishedAddressJa) continue;
  if (station.facilityNameJa && station.metadataStatus !== 'Official prefectural network placement facility') continue;
  if (station.facilityNameJa === facility) continue;
  station.facilityNameJa = facility;
  station.metadataStatus = 'Official prefectural network placement facility';
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'Osaka Prefecture names this facility as the installation place in its seismic-intensity network sheet; no street address was inferred.';
  changed += 1;
}
if (changed) writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${changed} station(s)`);
