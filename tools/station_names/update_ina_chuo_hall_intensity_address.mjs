import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1130130';
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '1130100').map((row) => row.code), [code]);
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '伊奈町中央');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '伊奈町役場',
  facilityNameEn: 'Ina Town Hall',
  publishedAddressJa: '埼玉県北足立郡伊奈町中央四丁目355番地',
  placementLocalityJa: '北足立郡伊奈町中央四丁目355番地',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct municipal disaster-plan instrument-host statement and published facility address',
  note: 'Ina disaster plan, April 2023 revision confirmed by its official parent index and cover, earthquake-response volume shinsai.pdf, printed page 2-139 / PDF page 139, section 2.3(1), explicitly states that the headquarters team checks the intensity meter installed at Town Hall. The opening/context and complete relevant page were text-read, and the host page was directly downloaded and visually inspected. The official Town Hall access guide explicitly publishes 埼玉県北足立郡伊奈町中央四丁目355番地. The fiscal 2026 implementation plan, issued February 2026, r8_zissikeikaku.pdf, cover and complete printed page 31 / PDF page 33 read and the table visually inspected, schedules the meter relocation associated with the replacement hall in fiscal 2027 (Reiwa 9), not as a completed move. The current construction notice, updated 9 June 2026, reports construction starting January 2026 and changed visitor parking, not a completed meter transfer. This record identifies the documented existing Town Hall site without assigning a replacement wing, room, floor, precise within-site position or physical move date. Composite matching uses the explicit host statement, published Chuo address and sole bundled Ina reporting station; no source is claimed to publish its seven-digit station code. English facility label is descriptive; approved station names remain unchanged. The dated plan statement is not represented as a fresh 2026 instrument inspection.',
});
for (const url of [
  'https://www.town.saitama-ina.lg.jp/cmsfiles/contents/0000007/7508/shinsai.pdf',
  'https://www.town.saitama-ina.lg.jp/0000007508.html',
  'https://www.town.saitama-ina.lg.jp/cmsfiles/contents/0000007/7508/hyoushi.pdf',
  'https://www.town.saitama-ina.lg.jp/0000000277.html',
  'https://www.town.saitama-ina.lg.jp/cmsfiles/contents/0000000/30/r8_zissikeikaku.pdf',
  'https://www.town.saitama-ina.lg.jp/0000009254.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Ina Town Hall address; protected-field and address/precision checks passed.`);
