import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1120831';
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '1120800').map((row) => row.code).sort(), ['1120820', '1120831']);
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '所沢市並木');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '所沢市役所',
  facilityNameEn: 'Tokorozawa City Hall',
  publishedAddressJa: '埼玉県所沢市並木一丁目1番地の1',
  placementLocalityJa: '所沢市並木一丁目1番地の1',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct municipal council instrument-host answers and published facility address',
  note: 'Tokorozawa Budget Standing Committee record dated 15 March 2022, r4.3.15yosan.pdf, printed page 31 / PDF page 30, explicitly records the crisis-management director placing the two reporting seismometers at Meiho Elementary School and the main City Hall building; the crisis-management office head independently confirms the sensors at Meiho and Tokorozawa City Hall and their observations being published by the meteorological observatory. The official committee index confirms the meeting date. Opening/context and the complete relevant page were text-read; the host page was also directly downloaded and visually inspected after the web screenshot failed. The current official City Hall facility guide publishes 埼玉県所沢市並木一丁目1番地の1. The municipal school guide independently places Meiho Elementary School in Kitayurakucho, distinguishing the already-addressed 1120820 host from this Namiki reporting station. Composite matching uses the explicit instrument-host answers and the two published host localities, not catalogue coordinates or proximity; the council record does not publish seven-digit station codes. This is a dated official host statement, not a fresh 2026 instrument inspection. No installation or transfer date, current room/floor, or precise within-site position is asserted. The English facility label is descriptive; approved station names and the other Tokorozawa record remain unchanged.',
});
for (const url of [
  'https://www.city.tokorozawa.saitama.jp/shigikai/tokubetuiinkaijyouhou/jyonininkai/yosankaigikiroku.files/r4.3.15yosan.pdf',
  'https://www.city.tokorozawa.saitama.jp/shigikai/tokubetuiinkaijyouhou/jyonininkai/yosankaigikiroku.html',
  'https://www.city.tokorozawa.saitama.jp/shisetu/shi/shi/',
  'https://www.city.tokorozawa.saitama.jp/shisetu/gakko/shogakko/meihousyougakkou.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Tokorozawa City Hall address; protected-field and address/precision checks passed.`);
