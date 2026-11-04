import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const placementUrl = 'https://www.city.adachi.tokyo.jp/documents/65301/20050825.pdf';
const updates = [
  ['1312152', '東京足立区神明南', '足立消防署神明出張所', 'Adachi Fire Station, Shinmei Branch', '東京都足立区神明南一丁目13番9号', 'https://www.tfd.metro.tokyo.lg.jp/fs/adati/index.html'],
  ['1312154', '東京足立区伊興', '西新井消防署', 'Nishiarai Fire Station', '東京都足立区伊興二丁目5番11号', 'https://www.tfd.metro.tokyo.lg.jp/fs/nisiarai/index.html'],
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
    placementLocalityJa: publishedAddressJa.replace(/^東京都/, ''),
    placementPrecision: 'exact_address',
    metadataStatus: 'Official ward instrument-host statement and current fire-service published address',
    note: `Adachi Ward's newsletter あだち広報 No. 1444, 25 August 2005, printed pages 4–5 (PDF page 4), explicitly states in 足立区内で震度5強 that intensity meters are installed at the Ward Office, Senju Fire Station, Adachi Fire Station Shinmei Branch and Nishiarai Fire Station. The complete relevant page was text-read and visually inspected. The Tokyo Fire Department's current host guide directly identifies ${facilityNameJa} at ${publishedAddressJa}, matching the reporting locality ${nameJa}. Neither source is claimed to include the seven-digit JMA station code. The ward statement is historical placement evidence, not a fresh 2026 inspection or a current within-site position; the current host guide supplies the published facility address, not a new instrument survey. Tokyo jurisdiction and half-width digits are normalized without changing the address numbers. Fire-service response areas, generic network membership, proximity and rounded coordinates are not used to infer this host. The separate Senju/Nakaicho record is not promoted from this old newsletter because the Senju host moved after publication and instrument relocation needs its own evidence. No room, floor, or exact within-site position is claimed. The English facility label is descriptive, not asserted as an official English title; approved English and Japanese station names remain unchanged.`,
  });
  for (const url of [placementUrl, addressUrl]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Adachi Shinmei/Nishiarai host-address updates; protected-field and address/precision checks passed.`);
