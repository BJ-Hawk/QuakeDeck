import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = new Map([
  ['2048230', {
    address: '長野県北安曇郡松川村76-5',
    facilityJa: '松川村役場',
    facilityEn: 'Matsukawa Village Office',
    url: 'https://www.vill.matsukawa.nagano.jp/'
  }],
  ['2032331', {
    address: '長野県北佐久郡御代田町大字馬瀬口1794-6',
    facilityJa: '御代田町役場',
    facilityEn: 'Miyota Town Office',
    url: 'https://www.town.miyota.nagano.jp/file/183047.pdf'
  }],
  ['2036130', {
    address: '長野県諏訪郡下諏訪町4613番地8',
    facilityJa: '下諏訪町役場',
    facilityEn: 'Shimosuwa Town Office',
    url: 'https://www.town.shimosuwa.lg.jp/www/genre/1000100000078/index.html'
  }],
  ['2045131', {
    address: '長野県東筑摩郡朝日村大字古見1555番地1',
    facilityJa: '朝日村役場',
    facilityEn: 'Asahi Village Office',
    url: 'https://www.vill.asahi.nagano.jp/official/sonseijoho/asahimuranoshokai/612.html'
  }],
  ['2038530', {
    address: '長野県上伊那郡南箕輪村4825番地1',
    facilityJa: '南箕輪村役場',
    facilityEn: 'Minamiminowa Village Office',
    url: 'https://www.vill.minamiminowa.lg.jp/soshiki/soumu/watasitatinomura.html'
  }],
  ['2038830', {
    address: '長野県上伊那郡宮田村98番地',
    facilityJa: '宮田村役場',
    facilityEn: 'Miyada Village Office',
    url: 'https://www.vill.miyada.nagano.jp/ck/2/files/2020sonsei.pdf'
  }],
  ['2042231', {
    address: '長野県木曽郡上松町大字上松159番地4',
    facilityJa: '上松町役場',
    facilityEn: 'Agematsu Town Office',
    url: 'https://www.town.agematsu.nagano.jp/aboutus/'
  }],
  ['2042330', {
    address: '長野県木曽郡南木曽町読書3668番地1',
    facilityJa: '南木曽町役場',
    facilityEn: 'Nagiso Town Office',
    url: 'https://www.town.nagiso.nagano.jp/soshiki_list.html'
  }],
  ['2042930', {
    address: '長野県木曽郡王滝村3623番地',
    facilityJa: '王滝村役場',
    facilityEn: 'Otaki Village Office',
    url: 'https://www.vill.otaki.nagano.jp/aboutus/gaiyou/facility/yakuba.html'
  }],
  ['2041431', {
    address: '長野県下伊那郡泰阜村3236番地1',
    facilityJa: '泰阜村役場',
    facilityEn: 'Yasuoka Village Office',
    url: 'https://www.vill.yasuoka.nagano.jp/access.html'
  }],
  ['2030730', {
    address: '長野県南佐久郡北相木村2744',
    facilityJa: '北相木村役場',
    facilityEn: 'Kitaaiki Village Office',
    url: 'https://www.vill.kitaaiki.nagano.jp/fs/7/9/1/2/0/_/__8__________.pdf'
  }],
  ['2044830', {
    address: '長野県東筑摩郡生坂村5493-2',
    facilityJa: '生坂村役場',
    facilityEn: 'Ikusaka Village Office',
    url: 'https://www.village.ikusaka.nagano.jp/'
  }],
  ['2045030', {
    address: '長野県東筑摩郡山形村2030番地1',
    facilityJa: '山形村役場',
    facilityEn: 'Yamagata Village Office',
    url: 'https://www.vill.yamagata.nagano.jp/government/'
  }],
  ['2041032', {
    address: '長野県下伊那郡根羽村2131番地1',
    facilityJa: '根羽村役場',
    facilityEn: 'Neba Village Office',
    url: 'https://www.nebamura.jp/'
  }],
  ['2040931', {
    address: '長野県下伊那郡平谷村354番地',
    facilityJa: '平谷村役場',
    facilityEn: 'Hiraya Village Office',
    url: 'https://pikan.net/contact'
  }],
  ['2041231', {
    address: '長野県下伊那郡売木村968-1',
    facilityJa: '売木村役場',
    facilityEn: 'Urugi Village Office',
    url: 'https://www.urugi.jp/'
  }],
  ['2041530', {
    address: '長野県下伊那郡喬木村6664番地',
    facilityJa: '喬木村役場',
    facilityEn: 'Takagi Village Office',
    url: 'https://www.vill.takagi.lg.jp/doc/2021070700025/'
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
  station.note = 'The official station label identifies this municipal office; the municipality publishes the office address.';
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
