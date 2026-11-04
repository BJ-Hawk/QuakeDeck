import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const planIndex = 'https://www.city.shiso.lg.jp/soshiki/machizukuri-bu/shobobosai/tantojoho/shisaku_keikaku/1386730083684.html';
const planUrl = 'https://www.city.shiso.lg.jp/material/files/group/8/R8-02_saigai-yobou.pdf';
const updates = [
  ['2822734', '宍粟市一宮町', '一宮市民協働センター（いちのぴあ）', 'Shiso City Ichinomiya Community Collaboration Center (Ichinopia)', '兵庫県宍粟市一宮町安積1347番地3', 'https://www.city.shiso.lg.jp/soshiki/chikusashiminkyoku/ichinomiyashiminkyoku/ichinomiyasyogaigakushujimusho/i_infomation/10613.html'],
  ['2822735', '宍粟市千種町', '千種市民協働センター（ライブリーちくさ）', 'Shiso City Chikusa Community Collaboration Center', '兵庫県宍粟市千種町千草168番地', 'https://www.city.shiso.lg.jp/shisetsu/gyoseishisetsu/1387185524629.html'],
];
for (const [code, nameJa, facilityNameJa, facilityNameEn, publishedAddressJa, addressUrl] of updates) {
  const s = data.stations.find((row) => row.code === code);
  assert.ok(s, `Missing station ${code}`);
  assert.equal(s.nameJa, nameJa);
  assert.equal(s.providerJa, '地方公共団体');
  assert.equal(s.placementPrecision, 'municipality_or_ward');
  assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa, `Existing placement ${code}`);
  Object.assign(s, {
    facilityNameJa,
    facilityNameEn,
    publishedAddressJa,
    placementLocalityJa: publishedAddressJa.replace(/^兵庫県/, ''),
    placementPrecision: 'exact_address',
    metadataStatus: 'Official adopted municipal installation inventory and current host-facility address',
    note: `Shiso City's adopted July 2026 regional disaster plan, volume 2 disaster prevention, section 2 地震観測施設の整備, printed page 災害予防計画-8 / PDF page 14, directly lists the prefectural intensity-meter hosts and addresses 一宮町安積1347-3 / 一宮市民協働センター内 and 千種町千草168 / 千種市民協働センター内. The table explicitly dates its installation inventory to 31 March 2024; publication in the 2026 plan is not claimed as a new 2026 instrument inspection. The complete relevant page was text-read and visually inspected. The separate NIED strong-motion and JMA Yume Park rows are not substituted for either prefectural instrument. The city's current facility guide identifies ${facilityNameJa} and publishes ${publishedAddressJa}; these current center identities supersede the shorter former 市民局 terminology on the older municipal fault-information page. Neither source is claimed to publish the seven-digit JMA code. The instrument-to-host link comes from the explicit installation inventory, not a coordinate, nearby building, response area, or locality-only guess. No room, floor, or precise within-site meter position is asserted. The English facility label is descriptive, not claimed as an official English title; approved Japanese and English station names remain unchanged.`,
  });
  for (const url of [planIndex, planUrl, addressUrl]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
}
const targets = new Set(updates.map(([code]) => code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((s) => s.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const s = data.stations[i];
  const old = before.stations[i];
  assert.equal(s.code, old.code);
  if (!targets.has(s.code)) assert.deepEqual(s, old);
  else {
    for (const key of new Set([...Object.keys(s), ...Object.keys(old)])) if (!allowed.has(key)) assert.deepEqual(s[key], old[key]);
    for (const url of old.sourceUrls) assert.ok(s.sourceUrls.includes(url));
  }
  assert.equal(s.placementPrecision === 'exact_address', Boolean(s.publishedAddressJa), `Address/precision mismatch ${s.code}`);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Shiso center host-address updates; protected-field and address/precision checks passed.`);
