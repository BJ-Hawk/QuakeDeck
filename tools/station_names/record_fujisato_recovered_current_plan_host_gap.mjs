import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  '5FA978D9B61FF2819B184541BD1928AEFC3573E9A5A150783FCF109A4E431FC5');
const data = JSON.parse(original), before = structuredClone(data);
const row = data.stations.find((r) => r.code === '0534631');
assert.ok(row);
assert.equal(row.nameJa, '藤里町藤琴');
assert.equal(row.approvedEnglishName, 'Fujikoto');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.publishedAddressJa && !row.facilityNameJa && !row.facilityNameEn);
assert.ok(!row.note.includes('bousaianzenshitsu/517'));

row.note += ' Public municipal-plan recovery checked on 17 October 2026: the indexed former plan page under town/soshiki/c54/syobo-bosai/bousai/517 returned 404. Following the live official organization index and the disaster-safety office recovered the current plan page at town/soshiki/c52/bousaianzenshitsu/517 and its actual linked R7 PDF files; no guessed replacement address, login or restricted access was used. Complete native text of the linked general-prevention volume ① (92 pages, 72955 extracted characters), general-response first part ② (84 pages, 66315 characters), earthquake-prevention volume ④ (38 pages, 25053 characters), earthquake-response volume ⑤ (31 pages, 20367 characters), and appendix ⑦ (75 pages, 56078 characters) was searched for whitespace-normalized instrument/network terms. Volumes ①, ② and ④ had no matches. Complete earthquake-response PDF page 16 / printed page 304 describes generic JMA early-warning detection, not a named town instrument host; complete appendix PDF page 9 is a generic JMA intensity-scale effects table. No individual installation facility or exact address was recovered in these text searches. This is not a complete visual/OCR inspection of all pages, and the unsearched general-response latter part ③ is not represented as inspected. The former contact address 藤琴字藤琴8 is not sensor-placement evidence. Do not repeat the obsolete index 404 or treat generic information reception, a nearby public facility or the separate KiK-net Fujisato station as this local-government reporting meter. Seek an individual prefectural installation/maintenance inventory, instrument-site contract or explicit municipal host statement. Facility/address fields and locality precision remain unchanged; approved names, coordinates and previous provenance are preserved.';
const root = 'https://www.town.fujisato.akita.jp/';
const urls = [
  `${root}town/soshiki/`,
  `${root}town/soshiki/c52/bousaianzenshitsu/`,
  `${root}town/soshiki/c52/bousaianzenshitsu/517`,
  ...[
    '①一般災害対策編（第１章総則～２章災害予防計画）【R7】.pdf',
    '②一般災害対策編（第３章災害応急対策計画　第１６節まで）【R7】.pdf',
    '④地震災害対策編（第１章総則～）【R7】.pdf',
    '⑤地震災害対策編（第３章災害応急対策計画～）【R7】.pdf',
    '⑦資料編【R7】.pdf',
  ].map((name) => `${root}up/files/shobo/${encodeURIComponent(name)}`),
];
for (const url of urls) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

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
console.log(`${apply ? 'Applied' : 'Would apply'} Fujisato recovered-plan host-gap provenance only; all placement/name/coordinate fields, unrelated records, order and prior provenance preserved; zero address/precision mismatches. Coverage delta: zero.`);
