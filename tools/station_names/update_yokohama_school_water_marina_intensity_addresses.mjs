import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'https://www.city.yokohama.lg.jp/bousai-kyukyu-bohan/bousai-saigai/data/shiryodata/jishinjoho/eq.html';
const eduBase = 'https://www.edu.city.yokohama.lg.jp/school/';
const updates = [
  ['1410641', '横浜保土ケ谷区上菅田町', '横浜市立上菅田中学校', 'Yokohama Municipal Kamisugeta Junior High School', '保土ケ谷区上菅田町780', `${eduBase}jhs/kamisugeta/index.cfm/1%2C0%2C42%2Chtml`],
  ['1410741', '横浜磯子区洋光台', '横浜市立洋光台第四小学校', 'Yokohama Municipal Yokodai No. 4 Elementary School', '磯子区洋光台六丁目6番1号', `${eduBase}es/yokodai4/`],
  ['1410941', '横浜港北区日吉本町', '横浜市立駒林小学校', 'Yokohama Municipal Komabayashi Elementary School', '港北区日吉本町二丁目51番地1号', `${eduBase}es/komabayashi/index.cfm/1%2C0%2C42%2Chtml`],
  ['1411040', '横浜戸塚区平戸町', '横浜市立平戸小学校', 'Yokohama Municipal Hirado Elementary School', '戸塚区平戸町542', `${eduBase}es/hirado/index.cfm/1%2C0%2C42%2C156%2Chtml`],
  ['1411540', '横浜栄区桂台南', '横浜市立桂台小学校', 'Yokohama Municipal Katsuradai Elementary School', '栄区桂台南1丁目1番地1号', `${eduBase}es/katsuradai/index.cfm/1%2C0%2C42%2Chtml`],
  ['1411840', '横浜都筑区池辺町', '横浜市立都田小学校', 'Yokohama Municipal Tsuda Elementary School', '都筑区池辺町2831番地', `${eduBase}es/tsuda/index.cfm/1%2C0%2C42%2C156%2Chtml`],
  ['1410143', '横浜鶴見区末広町', '北部第二水再生センター', 'Yokohama City Northern No. 2 Water Reclamation Center', '鶴見区末広町1-6-8', 'https://www.city.yokohama.lg.jp/kurashi/machizukuri-kankyo/kasen-gesuido/gesuido/center/saisei_center/centerinfo.html'],
  ['1410840', '横浜金沢区白帆', '横浜ベイサイドマリーナ', 'Yokohama Bayside Marina', '金沢区白帆1番地', 'https://www.ybmarina.com/marina/guide/'],
];
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((s) => s.code)).size, 4360);
for (const [code, nameJa, facilityNameJa, facilityNameEn, address, addressUrl] of updates) {
  const s = data.stations.find((item) => item.code === code);
  assert.ok(s, `Missing station ${code}`);
  assert.equal(s.nameJa, nameJa);
  assert.equal(s.providerJa, '地方公共団体');
  assert.equal(s.placementPrecision, 'municipality_or_ward');
  assert.ok(!s.publishedAddressJa && !s.facilityNameJa && !s.facilityNameEn, `Existing placement ${code}`);
  const qualification = code === '1410143'
    ? ' The facility directory separately lists 北部汚泥資源化センター at 末広町1-6-1; that different sludge-treatment facility address is not used.'
    : code === '1410840'
      ? ' The marina operator publishes 白帆1番地 in its facility guide and 神奈川県横浜市金沢区白帆1 in its contact page; its contact page expressly distinguishes the marina from Mitsui Outlet Park Yokohama Bayside. No shopping-mall address or particular marina building is substituted.'
      : ' The host school itself publishes its full municipal school identity and address. This evidence is not extended to a similarly named school or neighboring campus.';
  Object.assign(s, {
    facilityNameJa,
    facilityNameEn,
    publishedAddressJa: `神奈川県横浜市${address}`,
    placementLocalityJa: `横浜市${address}`,
    placementPrecision: 'exact_address',
    metadataStatus: 'Official municipal instrument-host inventory and host-authority published address',
    note: `Yokohama City's current strong-motion network inventory, updated 10 July 2026, explicitly names ${facilityNameJa.replace('横浜市立', '')} as an instrument installation site in this ward and states that observations from its 39-site network are supplied through Kanagawa Prefecture to JMA. The host-authority address source publishes 横浜市${address}. Together the inventory and host address reconcile observation label ${nameJa} to the uniquely matching locality among this ward's explicitly listed instrument hosts; neither page is claimed to publish the seven-digit JMA code.${qualification} The prefectural name is included as the address jurisdiction; full-width digits and hyphens are normalized without changing the published address numbers. No placement is inferred from coordinates, proximity, or locality alone. No room, floor, building within the site, or precise instrument position is asserted. English facility labels are descriptive translations, not claimed as official English titles. Approved station names remain unchanged.`,
  });
  const urls = [inventory, addressUrl];
  if (code === '1410840') urls.push('https://www.ybmarina.com/contact/');
  for (const url of urls) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Yokohama school/water/marina host-address updates; protected-field and address/precision checks passed.`);
