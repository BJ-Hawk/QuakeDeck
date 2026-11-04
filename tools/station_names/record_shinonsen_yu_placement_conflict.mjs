import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2858631';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '新温泉町湯');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
const marker = 'Shinonsen placement conflict checked against the February 2025 adopted earthquake plan';
assert.ok(!station.note.includes(marker), 'This research note is already recorded');
station.note += ` ${marker}, 1740557053.pdf, printed page 39 / PDF page 50: the full relevant page explicitly lists the Onsen General Branch Office at Yu 1604 as an intensity-observation facility. Its cover and complete relevant page were text-read and the relevant page visually inspected. However, the official December 2006 newsletter, 061214.pdf, complete printed/PDF page 5, text-read and visually inspected, announces relocation of the branch to Yu 990-8, with ordinary service starting 4 January 2007. The current official office guide independently places the Onsen branch / town community center at Yu 990-8, while the current department page distinguishes that address from Yakushiyu at Yu 1604. The 2025 plan therefore does not by itself resolve whether the reporting meter remains on the former site, moved with the branch, or is described using stale office/address text. No inspected source establishes the instrument's completed relocation or current parcel. Both facility fields and publishedAddressJa remain blank; no exact-address or facility-only promotion is made. Next seek a current instrument installation inventory or explicit sensor-relocation/continuity evidence, not another ordinary office address or catalogue coordinates.`;
for (const url of [
  'https://www.town.shinonsen.hyogo.jp/uppdf/1740557053.pdf',
  'https://www.town.shinonsen.hyogo.jp/shinonsen/kouhou/061214.pdf',
  'https://www.town.shinonsen.hyogo.jp/page/?mode=detail&page_id=2bfcaf22a8b15dbc485514b0e46671c9',
  'https://www.town.shinonsen.hyogo.jp/section_detail.php?id=3b1c1ffc224d684c69d6bbadf6b2bf9c',
]) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);

const allowed = new Set(['note', 'sourceUrls']);
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
console.log(`${apply ? 'Recorded' : 'Would record'} ${code} conflicting placement evidence only; no host/address promotion; protected-field checks passed.`);
