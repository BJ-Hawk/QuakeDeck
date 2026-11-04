import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const inventory = 'https://www.city.yokohama.lg.jp/bousai-kyukyu-bohan/bousai-saigai/data/shiryodata/jishinjoho/eq.html';
const fireBase = 'https://www.city.yokohama.lg.jp/bousai-kyukyu-bohan/shobo/shobosho/';
const updates = [
  ['1410242', '横浜神奈川区広台太田町', '神奈川消防署', 'Kanagawa Fire Station', '神奈川区広台太田町3-8', 'kanagawa/kanagawa-top.html'],
  ['1410441', '横浜中区山吹町', '中消防署', 'Naka Fire Station', '中区山吹町2番地2', 'naka/naka-top.html'],
  ['1410541', '横浜南区六ツ川', '六ツ川消防出張所', 'Mutsukawa Fire Branch Station', '南区六ツ川一丁目693番地1', 'minami/minami-shuttyou.html'],
  ['1410542', '横浜南区大岡', '大岡消防出張所', 'Ooka Fire Branch Station', '南区大岡四丁目7番15号', 'minami/minami-shuttyou.html'],
  ['1411142', '横浜港南区野庭町', '野庭消防出張所', 'Noba Fire Branch Station', '港南区野庭町638番2号', 'konan/kounan-shuttyou.html'],
  ['1411143', '横浜港南区丸山台北部', '上永谷消防出張所', 'Kaminagaya Fire Branch Station', '港南区丸山台三丁目6番1号', 'konan/kounan-shuttyou.html'],
  ['1411242', '横浜旭区川井宿町', '都岡消防出張所', 'Tsuoka Fire Branch Station', '旭区川井宿町4番地1', 'asahi/asahi-shuttyou.html'],
  ['1410842', '横浜金沢区釜利谷南', '釜利谷消防出張所', 'Kamariya Fire Branch Station', '金沢区釜利谷南三丁目22番2号', 'kanazawa/kanazawa-shuttyou.html'],
  ['1410942', '横浜港北区綱島西', '綱島消防出張所', 'Tsunashima Fire Branch Station', '港北区綱島西三丁目3番9号', 'kohoku/kouhoku-shuttyou.html'],
  ['1411342', '横浜緑区鴨居', '鴨居消防出張所', 'Kamoi Fire Branch Station', '緑区鴨居一丁目3番21号', 'midori/midorishuchouzyo.html'],
  ['1411742', '横浜青葉区美しが丘', '元石川消防出張所', 'Motoishikawa Fire Branch Station', '青葉区美しが丘4丁目12番地', 'aoba/aoba-shuttyou.html'],
  ['1411743', '横浜青葉区鴨志田町', '鴨志田消防出張所', 'Kamoshida Fire Branch Station', '青葉区鴨志田町823番地', 'aoba/aoba-shuttyou.html'],
  ['1411042', '横浜戸塚区鳥が丘', '鳥が丘消防出張所', 'Torigaoka Fire Branch Station', '戸塚区鳥が丘52-1', 'totsuka/totsuka-shuttyou.html'],
  ['1411640', '横浜泉区岡津町', '岡津消防出張所', 'Okazu Fire Branch Station', '泉区岡津町187番地1', 'izumi/izumi-shuttyou.html'],
  ['1411440', '横浜瀬谷区中屋敷', '中瀬谷消防出張所', 'Nakaseya Fire Branch Station', '瀬谷区中屋敷二丁目16番地15', 'seya/seya-shuttyou.html'],
  ['1411842', '横浜都筑区茅ケ崎', '都筑消防署', 'Tsuzuki Fire Station', '都筑区茅ケ崎中央32番1号', 'tsuzuki/tsuzuki-top.html'],
];
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((s) => s.code)).size, 4360);
for (const [code, nameJa, facilityNameJa, englishHost, address, page] of updates) {
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
    metadataStatus: 'Official municipal instrument-host inventory and fire-service facility address',
    note: `Yokohama City's current strong-motion network page, updated 10 July 2026, explicitly lists ${facilityNameJa === '六ツ川消防出張所' ? '六ッ川消防出張所 (the fire-service guide spells the same branch 六ツ川消防出張所)' : facilityNameJa} as an instrument installation site in this ward. The city explains that observations from its 39-site network are supplied through Kanagawa Prefecture to JMA. Its official fire-service facility guide directly publishes 横浜市${address} for this named host. Together these sources reconcile observation label ${nameJa} to the uniquely matching host locality among the ward's explicitly listed instrument sites; neither source is claimed to include the seven-digit JMA station code. The prefectural name is included as the address jurisdiction and full-width digits are normalized without changing the published address numbers. Fire-service jurisdiction/response areas are not treated as instrument placements, and no meter position is inferred from an office-floor description. No room, floor, or precise within-site position is asserted. The English facility label is descriptive, not claimed as an official English title; approved station names remain unchanged.`,
  });
  for (const url of [inventory, `${fireBase}${page}`]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Yokohama fire-host address updates; protected-field and address/precision checks passed.`);
