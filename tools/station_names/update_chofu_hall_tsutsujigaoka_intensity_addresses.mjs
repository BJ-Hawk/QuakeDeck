import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const reportUrl = 'https://www.city.chofu.lg.jp/documents/4449/kiroku.pdf';
const updates = [
  ['1320830', '調布市小島町', '調布市役所', 'Chofu City Hall', '東京都調布市小島町2-35-1', 'https://www.city.chofu.lg.jp/010040/p003004.html'],
  ['1320850', '調布市西つつじヶ丘', '調布消防署つつじヶ丘出張所', 'Chofu Fire Station Tsutsujigaoka Branch', '東京都調布市西つつじヶ丘三丁目8番地3', 'https://www.tfd.metro.tokyo.lg.jp/fs/cyoufu/index.html'],
];

for (const [code, nameJa, facilityNameJa, facilityNameEn, publishedAddressJa, addressUrl] of updates) {
  const s = data.stations.find((row) => row.code === code);
  assert.ok(s, `Missing station ${code}`);
  assert.equal(s.nameJa, nameJa);
  assert.equal(s.providerJa, '地方公共団体');
  assert.equal(s.placementPrecision, 'municipality_or_ward');
  assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa, `Existing placement ${code}`);
  const addressEvidence = code === '1320830'
    ? 'The official Chofu City Hall access page publishes 東京都調布市小島町2-35-1; its indexed official text was checked, while direct page retrieval returned 403.'
    : 'The current Tokyo Fire Department Chofu station guide explicitly identifies つつじヶ丘出張所 and publishes 東京都調布市西つつじヶ丘三丁目８番地３. This is not the main Chofu Fire Station in Shimoishiwara or the municipal Jindai branch office.';
  Object.assign(s, {
    facilityNameJa,
    facilityNameEn,
    publishedAddressJa,
    placementLocalityJa: publishedAddressJa.replace(/^東京都/, ''),
    placementPrecision: 'exact_address',
    metadataStatus: 'Historical official council report of administrative instrument-host statement and published facility address',
    note: `Chofu City Council report meeting of 14 November 2015, kiroku.pdf, printed/PDF page 8, records the administration's statement that the city's two reporting intensity meters are installed at City Hall and Tokyo Fire Department Tsutsujigaoka Branch. The same passage distinguishes the Kojimacho and Tsutsujigaoka observations. The cover and complete relevant page were text-read through the web service's cached 37-page PDF text. Direct PDF download returned 404 and screenshot retrieval failed; no visual verification or fresh source download is claimed. This resolves the formerly incomplete search-preview-only check using the full relevant text. ${addressEvidence} These are dated host statements paired with published facility addresses, not fresh 2026 meter inspections. No source is claimed to contain the seven-digit JMA code, and no host is inferred from coordinates or proximity. No current room, floor, or precise within-site meter position is asserted. The English facility label is descriptive, not claimed as an official English title; approved English and Japanese station names remain unchanged.`,
  });
  for (const url of [reportUrl, addressUrl]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Chofu host-address updates; protected-field and address/precision checks passed.`);
