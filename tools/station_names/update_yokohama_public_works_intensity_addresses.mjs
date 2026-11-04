import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const sources = [
  'https://www.city.yokohama.lg.jp/bousai-kyukyu-bohan/bousai-saigai/data/shiryodata/jishinjoho/eq.html',
  'https://www.city.yokohama.lg.jp/city-info/yokohamashi/org/doro/sonota/20140225170306.html',
];
const updates = [
  ['1410240', '横浜神奈川区神大寺', '神奈川土木事務所', 'Kanagawa Public Works Office', '神奈川区神大寺2-28-22'],
  ['1410341', '横浜西区浜松町', '西土木事務所', 'Nishi Public Works Office', '西区浜松町12-6'],
  ['1410440', '横浜中区山下町', '中土木事務所', 'Naka Public Works Office', '中区山下町246'],
  ['1410640', '横浜保土ケ谷区神戸町', '保土ケ谷土木事務所', 'Hodogaya Public Works Office', '保土ケ谷区神戸町61'],
  ['1411240', '横浜旭区今宿東町', '旭土木事務所', 'Asahi Public Works Office', '旭区今宿東町1555'],
  ['1410740', '横浜磯子区磯子', '磯子土木事務所', 'Isogo Public Works Office', '磯子区磯子3-14-45'],
  ['1410841', '横浜金沢区寺前', '金沢土木事務所', 'Kanazawa Public Works Office', '金沢区寺前1-9-26'],
  ['1410940', '横浜港北区大倉山', '港北土木事務所', 'Kohoku Public Works Office', '港北区大倉山7-39-1'],
  ['1411341', '横浜緑区十日市場町', '緑土木事務所', 'Midori Public Works Office', '緑区十日市場町876-13'],
  ['1411041', '横浜戸塚区戸塚町', '戸塚土木事務所', 'Totsuka Public Works Office', '戸塚区戸塚町2974-1'],
  ['1411541', '横浜栄区小菅ケ谷', '栄土木事務所', 'Sakae Public Works Office', '栄区小菅ケ谷1-6-1'],
  ['1411641', '横浜泉区和泉中央北', '泉土木事務所', 'Izumi Public Works Office', '泉区和泉中央北5-1-2'],
  ['1411441', '横浜瀬谷区三ツ境', '瀬谷土木事務所', 'Seya Public Works Office', '瀬谷区三ツ境153-7'],
];
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((s) => s.code)).size, 4360);
for (const [code, nameJa, facilityNameJa, englishHost, address] of updates) {
  const s = data.stations.find((item) => item.code === code);
  assert.ok(s, `Missing station ${code}`);
  assert.equal(s.nameJa, nameJa);
  assert.equal(s.providerJa, '地方公共団体');
  assert.equal(s.placementPrecision, 'municipality_or_ward');
  assert.ok(!s.publishedAddressJa && !s.facilityNameJa && !s.facilityNameEn, `Existing placement ${code}`);
  Object.assign(s, {
    facilityNameJa,
    facilityNameEn: `Yokohama City ${englishHost}`,
    publishedAddressJa: `神奈川県横浜市${address}`,
    placementLocalityJa: `横浜市${address}`,
    placementPrecision: 'exact_address',
    metadataStatus: 'Official municipal instrument-host inventory and published facility address',
    note: `Yokohama City's current strong-motion network page, updated 10 July 2026, explicitly lists ${facilityNameJa} as an instrument installation site in this ward and explains that the 39-site city network supplies observations through Kanagawa Prefecture to JMA. The city's public-works directory, updated 25 April 2025, publishes 横浜市${address} for that named host. This combination matches the observation label ${nameJa} to the uniquely matching host locality among the explicitly listed city-network facilities in the ward; neither page is claimed to publish the seven-digit JMA code. The prefectural name is included as the address jurisdiction. The host is not inferred merely from a nearby office, rounded coordinates, or a generic network-participation statement. Offices present in the address directory but absent from the instrument-host inventory are not assigned. No room, floor, or precise within-site meter position is asserted. The English facility label is descriptive, not claimed as an official English title. Existing approved English and Japanese station names are unchanged.`,
  });
  for (const url of sources) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
}
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
const targets = new Set(updates.map(([code]) => code));
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const s = data.stations[i];
  const old = before.stations[i];
  assert.equal(s.code, old.code);
  if (!targets.has(s.code)) assert.deepEqual(s, old);
  else {
    for (const key of new Set([...Object.keys(s), ...Object.keys(old)])) {
      if (!allowed.has(key)) assert.deepEqual(s[key], old[key], `${s.code} protected field ${key}`);
    }
    for (const url of old.sourceUrls) assert.ok(s.sourceUrls.includes(url));
  }
  assert.equal(s.placementPrecision === 'exact_address', Boolean(s.publishedAddressJa), `Address/precision mismatch ${s.code}`);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Yokohama public-works host/address updates; protected-field and address/precision checks passed.`);
