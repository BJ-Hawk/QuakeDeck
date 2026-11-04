import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html';
const updates = [
  ['1120930', '飯能市双柳', 'Namiyanagi', 'SIT.009', '飯能市役所', 'Hanno City Hall', '埼玉県飯能市大字双柳1番地の1', 'https://www.city.hanno.lg.jp/shiseijoho/yakusho_madoguchiannai/5516.html'],
  ['1121230', '東松山市松葉町', 'Matsubacho', 'SIT.012', '東松山市役所', 'Higashimatsuyama City Hall', '埼玉県東松山市松葉町1-1-58', 'https://www.city.higashimatsuyama.lg.jp/mobile/soshiki/6/4235.html'],
  ['1121530', '狭山市入間川', 'Irumagawa', 'SIT.015', '狭山市役所', 'Sayama City Hall', '埼玉県狭山市入間川1丁目23番5号', 'https://www.city.sayama.saitama.jp/shisei/shisetsu/shiyakusho/index.html'],
  ['1121730', '鴻巣市中央', 'Chuo', 'SIT.017', '鴻巣市役所', 'Konosu City Hall', '埼玉県鴻巣市中央1番1号', 'https://www.city.kounosu.saitama.jp/page/3132.html'],
  ['1121931', '上尾市本町', 'Honcho', 'SIT.019', '上尾市役所', 'Ageo City Hall', '埼玉県上尾市本町三丁目1番1号', 'https://www.city.ageo.lg.jp/page/15-access.html'],
  ['1122530', '入間市豊岡', 'Toyoka', 'SIT.025', '入間市役所', 'Iruma City Hall', '埼玉県入間市豊岡一丁目16番1号', 'https://www.city.iruma.saitama.jp/search_shisetsu/158/8337.html'],
  ['1122730', '朝霞市本町', 'Honcho', 'SIT.027', '朝霞市役所', 'Asaka City Hall', '埼玉県朝霞市本町1-1-1', 'https://www.city.asaka.lg.jp/soshiki/8/siyakusho.html'],
  ['1122930', '和光市広沢', 'Hirosawa', 'SIT.029', '和光市役所', 'Wako City Hall', '埼玉県和光市広沢1-5', 'https://www.city.wako.lg.jp/faq/f-shisetsu/1008792.html'],
  ['1123031', '新座市野火止', 'Nobitome', 'SIT.030', '新座市役所', 'Niiza City Hall', '埼玉県新座市野火止1-1-1', 'https://www.city.niiza.lg.jp/map/shiyakushohonchoshadai2chosha.html'],
  ['1123530', '富士見市鶴馬', 'Tsuruma', 'SIT.035', '富士見市役所', 'Fujimi City Hall', '埼玉県富士見市大字鶴馬1800番地の1', 'https://www.city.fujimi.saitama.jp/madoguchi_shisetsu/01shisetsu/siyakysho/index.html'],
  ['1124130', '鶴ヶ島市三ツ木', 'Mitsugi', 'SIT.041', '鶴ヶ島市役所', 'Tsurugashima City Hall', '埼玉県鶴ヶ島市大字三ツ木16番地1', 'https://www.city.tsurugashima.lg.jp/gyousei/gaiyou/page002093.html'],
  ['1124230', '日高市南平沢', 'Minamihirasawa', 'SIT.042', '日高市役所', 'Hidaka City Hall', '埼玉県日高市大字南平沢1020番地', 'https://www.city.hidaka.lg.jp/information/33/38/chosyanogoannai.html'],
  ['1124331', '吉川市きよみ野', 'Kiyomino', 'SIT.043', '吉川市役所', 'Yoshikawa City Hall', '埼玉県吉川市きよみ野一丁目1番地', 'https://www.city.yoshikawa.saitama.jp/index.cfm/27,735,172,1200,html'],
];
for (const [code, nameJa, english, id, host, facilityNameEn, address, addressSource] of updates) {
  const row = data.stations.find((station) => station.code === code);
  assert.ok(row, `Missing ${code}`);
  assert.equal(row.nameJa, nameJa);
  assert.equal(row.approvedEnglishName, english);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa, `Existing placement ${code}`);
  Object.assign(row, { facilityNameJa: host, facilityNameEn, publishedAddressJa: address,
    placementLocalityJa: address, placementPrecision: 'exact_address',
    metadataStatus: 'Public university installation inventory (2026) and independently published official city-hall address' });
  let qualification = 'The official municipal hall/access guide independently identifies the named City Hall and publishes the recorded site address.';
  if (code === '1121730') qualification += ' The city guide distinguishes its main and new buildings on the same named site; no particular wing or department floor is assigned to the instrument.';
  if (code === '1122530') qualification += ' The official main-office guide publishes the existing Toyoka 1-16-1 site. Building reconstruction/access works are not asserted to establish a new meter position, transfer date or replacement-wing placement.';
  if (code === '1123031') qualification += ' The current guide covers the main and second office buildings; the instrument is not assigned to a particular building, floor or rebuilt wing from a floor plan.';
  if (code === '1124130') qualification += ' The official access-page body was retrieved and read directly; a separate web-reader request returned a JavaScript verification page, not a different address.';
  if (code === '1124331') qualification += ' The guide identifies the current Kiyomino office address, not the former hall location; its map and department listings are not interpreted as a precise sensor position.';
  row.note += ` Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Saitama observation inventory explicitly names ${host} in the installation-facility column for ${id}. Its heading states last updated 16 May 2026. The complete selected row and column headings were read without logging in, importing the catalogue or retrieving restricted waveform data. ${qualification} The published host-site address is recorded as ${address}. The host relationship comes from the explicit installation inventory, not a generic rule that municipality stations are at City Hall, a contact address alone, rounded coordinates or proximity. Association with this reporting record uses the explicit facility identity and independently published matching locality, not a source-printed seven-digit JMA-code crosswalk. The inventory page date is retained as provenance, not asserted as an individual physical instrument inspection or full operational-continuity recertification. No room, floor, precise within-site sensor position, installation/transfer date or physical visit is asserted. Facility English is descriptive, not claimed as an official English title; approved Japanese and English station names, coordinates and previous provenance remain unchanged.`;
  for (const url of [inventory, addressSource]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}
const codes = new Set(updates.map(([code]) => code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(codes.size, 13);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((station) => station.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  if (!codes.has(row.code)) assert.deepEqual(row, old);
  else {
    assert.deepEqual(Object.keys(row), Object.keys(old));
    for (const key of Object.keys(old)) if (!allowed.has(key)) assert.deepEqual(row[key], old[key]);
    assert.ok(row.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} thirteen Saitama City Hall host-address records; all protected fields, other records, order and previous provenance preserved; zero address/precision mismatches. Coverage delta only: exact +13, municipality/ward -13, remaining facility/address research -13; facility-only unchanged.`);
