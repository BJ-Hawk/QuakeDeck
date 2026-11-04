import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2822632';
const s = data.stations.find((row) => row.code === code);
assert.ok(s, `Missing station ${code}`);
assert.equal(s.nameJa, '淡路市郡家');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa, `Existing placement ${code}`);
Object.assign(s, {
  facilityNameJa: '淡路市一宮事務所',
  facilityNameEn: 'Awaji City Ichinomiya Office',
  publishedAddressJa: '兵庫県淡路市郡家170番地1',
  placementLocalityJa: '淡路市郡家170番地1',
  placementPrecision: 'exact_address',
  metadataStatus: 'Primary university field-survey instrument-host statements and official municipal facility address',
  note: 'Kobe University investigators 鍬田泰子 and 上仲亮, field survey of 13 April 2013, report20130413eq.pdf page 1, explicitly locate the Gunge instrument behind Ichinomiya General Office. Independently, Hiroshima University investigators 三浦弘之 and 真鍋良輔 surveyed the observation sites on 17 April 2013; their report, created 18 April and updated 19 April 2013, page 8, explicitly identifies 淡路市郡家 as installed in the parking area behind Awaji City Ichinomiya Office and includes its photograph. The complete relevant pages were text-read, and the Hiroshima page was visually inspected. JMA\'s 13 April 2013 Kobe field report, page 9, independently identifies and photographs the prefectural Gunge instrument; its location map is corroboration, not a proximity-based inference of a host. Awaji City\'s official Ichinomiya Office guide directly publishes 兵庫県淡路市郡家170番地1, distinct from the separate main City Hall address in the site footer. These are historical direct host observations paired with the published office address, not a fresh 2026 instrument inspection. The historical rear-parking description is retained as source context, not asserted as a verified current within-site position. No current room, floor, or precise meter position is claimed. No facility is inferred from catalogue coordinates or locality alone. The English facility label is descriptive, not claimed as an official English title; approved Japanese and English station names remain unchanged.',
});
for (const url of [
  'https://www2.kobe-u.ac.jp/~kuwata/earthquake/report20130413eq.pdf',
  'https://home.hiroshima-u.ac.jp/hmiura/pdf/awaji_report130417_hmiura.pdf',
  'https://www.data.jma.go.jp/eqev/data/oshirase/2013/20130413jma_mot_kobe.pdf',
  'https://www.city.awaji.lg.jp/soshiki/koushitsu/ichinomiyajimusyo.html',
]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((row) => row.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const current = data.stations[i];
  const old = before.stations[i];
  assert.equal(current.code, old.code);
  if (current.code !== code) assert.deepEqual(current, old);
  else {
    for (const key of new Set([...Object.keys(current), ...Object.keys(old)])) if (!allowed.has(key)) assert.deepEqual(current[key], old[key]);
    for (const url of old.sourceUrls) assert.ok(current.sourceUrls.includes(url));
  }
  assert.equal(current.placementPrecision === 'exact_address', Boolean(current.publishedAddressJa), `Address/precision mismatch ${current.code}`);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Ichinomiya Office host-address update; protected-field and address/precision checks passed.`);
