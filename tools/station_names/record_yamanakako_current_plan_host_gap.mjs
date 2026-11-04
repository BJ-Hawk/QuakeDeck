import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  '6F7832496E1A947B4824998FAB9BA23F9537069B5E57C693424AF2C22FF4E849');
const data = JSON.parse(original), before = structuredClone(data);
const row = data.stations.find((r) => r.code === '1942531');
assert.ok(row);
assert.equal(row.nameJa, '山中湖村山中');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.publishedAddressJa && !row.facilityNameJa && !row.facilityNameEn);
assert.ok(!row.note.includes('R7BOUSAIKEIKAKU-HONPEN_marked.pdf'));
row.note += ' Current municipal-plan source recovery checked on 17 October 2026: Yamanakako Village publication info/41 states that its disaster council amended the plan on 26 March 2025, with publication updated 22 April 2025. Its normal public PDF links resolve through Link/Pdf/763 and Link/Pdf/764 to R7BOUSAIKEIKAKU-HONPEN_marked.pdf and R7BOUSAIKEIKAKU-SIRYOUHEN_marked.pdf. Both files were recovered with a normal browser-style public request, without credentials or restricted access. All 403 main-plan pages were searched as native PDF text for whitespace-normalized instrument/network terms (414815 extracted characters, with low-text pages retained as an extraction limitation); matches were PDF pages 284 and 322. Complete PDF page 284, printed page 276, was text-read, rendered and visually inspected with its earthquake-response heading and complete diagram. 計測震度計 is one input to the general-affairs director in a communications/mobilization flow, alongside television/radio and prefectural radio; the diagram does not publish an installation facility or address. PDF page 322 concerns generic Tokai-earthquake observation/notification arrangements, not a named village instrument host. All 229 appendix pages were also searched as native text with no instrument/network match; this was not a complete visual/OCR examination of every appendix or low-text page. The publication contact at 山中237-1 and references to staff assembling at the village office do not establish that the reporting meter is installed there. Facility/address fields and locality precision therefore remain unchanged; seek an individual installation inventory, instrument-site contract or explicit host statement rather than repeat these plan text searches. No placement was inferred from coordinates, map markers, contact-address proximity or a communications diagram. Approved names, coordinates and prior provenance remain unchanged.';
for (const url of [
  'https://www.vill.yamanakako.lg.jp/info/41',
  'https://www.vill.yamanakako.lg.jp/div/bousai/pdf/bousai/R7BOUSAIKEIKAKU-HONPEN_marked.pdf',
  'https://www.vill.yamanakako.lg.jp/div/bousai/pdf/bousai/R7BOUSAIKEIKAKU-SIRYOUHEN_marked.pdf',
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
console.log(`${apply ? 'Applied' : 'Would apply'} Yamanakako current-plan host-gap provenance only; placement fields, names, coordinates, unrelated records, order and previous provenance preserved; zero address/precision mismatches. Coverage delta: zero.`);
