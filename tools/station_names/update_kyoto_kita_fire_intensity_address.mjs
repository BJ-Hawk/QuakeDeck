import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2610142';
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '京都北区大宮西脇台町');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '京都市北消防署',
  facilityNameEn: 'Kyoto City Kita Fire Station',
  publishedAddressJa: '京都府京都市北区大宮西脇台町17番地の2',
  placementLocalityJa: '京都市北区大宮西脇台町17番地の2',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct current municipal earthquake-observatory facility/address inventory and official fire-service address',
  note: 'Kyoto City disaster-plan index, updated 13 May 2026, links its 12 December 2025 edition through the official public e-book library. Appendix volume 資料編【資料2】, printed page 72 / e-book page 38, table 資料2-3-3-1 京都市地震観測所, explicitly dated as of 1 July 2025, row 1, directly identifies the city-managed Kita earthquake observation site as 北消防署 at 北区大宮西脇台町17-2. The current e-book page-text index was searched, and its public single-page PDF 0038.pdf was downloaded in memory, fully text-read and visually inspected. The old siryou1.pdf URL returned 404 and was not used as current evidence. The official fire-service facility directory dated 21 April 2025 independently publishes 北区大宮西脇台町17番地の2. The official fire-station relocation notice dated 26 March 2021 distinguishes this new address from the former 北区紫竹下緑町87番地 and announces fire-station operation from 29 March 2021; that building-opening time is not asserted as the physical sensor-transfer time. Matching uses the direct municipal observation-site inventory, city/locality/provider identity and independently published address, not coordinates or proximity. The inventory is not claimed to publish the seven-digit station code, current room, floor, precise within-site sensor position or a fresh 2026 meter inspection. English facility label is descriptive; approved station names remain unchanged.',
});
for (const url of [
  'https://www.bousai.city.kyoto.lg.jp/0000000148.html',
  'https://bousai-city-kyoto.meclib.jp/library/site/public/book-lists/',
  'https://bousai-city-kyoto.meclib.jp/library/books/bk-siryou02/book/#target/page_no=38',
  'https://bousai-city-kyoto.meclib.jp/library/books/bk-siryou02/book/pdf/0038.pdf',
  'https://www.city.kyoto.lg.jp/shobo/page/0000159860.html',
  'https://www.city.kyoto.lg.jp/shobo/page/0000282514.html',
]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);

const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((row) => row.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  if (row.code !== code) assert.deepEqual(row, old);
  else {
    for (const key of new Set([...Object.keys(row), ...Object.keys(old)])) if (!allowed.has(key)) assert.deepEqual(row[key], old[key]);
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Kyoto City Kita Fire Station address; protected-field and address/precision checks passed.`);
