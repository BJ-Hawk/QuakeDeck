import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  '283028746B24F0E5A758DB3B404C58E217D0565E5F4D9C902621FA7877B2BD15');
const data = JSON.parse(original), before = structuredClone(data);
const row = data.stations.find((r) => r.code === '3321538');
assert.ok(row);
assert.equal(row.nameJa, '美作市美来');
assert.equal(row.approvedEnglishName, 'Mirai');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
assert.ok(!row.note.includes('4897-26'));

row.note += ' Official public-GIS follow-up checked on 17 October 2026: Okayama Prefecture disaster map 7, selected layer 26 震度情報NWS, was read through its normal free-viewing agreement without login, registration or restricted access. The map terms describe this layer as instrument installation places, while warning that source data may not be current and that address searches are approximate. A normal selected-layer facility-name search for 美作 (InfoSearch/Search, mtl=26, mcl=26,125,1,1006, skwt=1) returned one record, fid 4897-26, named 美作市役所, whose complete published attributes explicitly give 設置場所 美作市栄町38-2. Its remaining numbered fields and remarks are blank; the other populated fields are instrument coordinates, which were not used to infer an address. A separate all-attribute search for 美来 (skwt=0) returned zero records. The feature-ID GetAttribute request returned recordcount 0 and no attribute rows, so no additional detail or date was recovered there. The named search record therefore preserves the former Sakaemachi host/address, not completed placement evidence for successor Mirai. No seismic-layer update date was established; the shelter-layer date is unrelated. Together with the already-saved JMA relocation dates this rules out assigning the old GIS address to current station 3321538, but does not establish its new host. No map coordinates, marker position, address geocoding or bulk catalogue import was used. Facility/address fields and locality precision remain unchanged; seek a public as-built drawing, completed meter-transfer record or updated individual installation inventory. Approved names, coordinates and prior provenance remain unchanged.';
for (const url of [
  'https://www.gis.pref.okayama.jp/pref-okayama/Agreement?IsPost=False&MapId=7&RequestPage=%2Fpref-okayama%2FPositionSelect%3Fmid%3D7',
  'https://www.gis.pref.okayama.jp/pref-okayama/Map?mid=7',
  'https://www.gis.pref.okayama.jp/pref-okayama/InfoSearch/Search',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((r) => r.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const r = data.stations[i], old = before.stations[i];
  assert.equal(r.code, old.code);
  if (r.code !== row.code) assert.deepEqual(r, old);
  else {
    assert.deepEqual(Object.keys(r), Object.keys(old));
    for (const key of Object.keys(old)) if (!['note', 'sourceUrls'].includes(key)) assert.deepEqual(r[key], old[key]);
    assert.ok(r.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(r.sourceUrls.includes(url));
  }
  assert.equal(r.placementPrecision === 'exact_address', Boolean(r.publishedAddressJa),
    `Address/precision mismatch ${r.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} Mimasaka obsolete-GIS qualification only; all placement/name/coordinate fields, unrelated records, order and prior provenance preserved; zero address/precision mismatches. Coverage delta: zero.`);
