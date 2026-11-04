import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1311830';
const row = data.stations.find((station) => station.code === code);
assert.ok(row);
assert.equal(row.nameJa, '東京荒川区荒川');
assert.equal(row.approvedEnglishName, 'Arakawa');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
const marker = 'Arakawa former-disaster-center continuity evidence checked on 17 October 2026:';
assert.ok(!row.note.includes(marker), 'Already recorded');
row.note += ` ${marker} the public University of Tokyo Earthquake Research Institute SK-net Tokyo metropolitan installation inventory, heading last updated 2 January 2021, explicitly names 荒川区防災センター as the host for TKY.1180. The complete selected row and column headings were read. Arakawa's fiscal 2018 facility-analysis sheet, PDF page 1 (facility code S04-05-01), independently names 防災センター and publishes 荒川区荒川二丁目25番3号. Its complete page was text-read, rendered and visually inspected; the office/room descriptions do not locate the instrument. The official February 2024 New City Hall Development Basic Policy, printed page 9 / PDF page 11, explicitly states that the disaster department moved from the annex (former Disaster Prevention Center) to the main office's third floor in fiscal 2019, with some equipment remaining at the annex. Its cover and complete relevant page were text-read, and the relevant page rendered and visually inspected. This is department/equipment context, not a statement identifying which equipment remained or proving a seismometer transfer. Search-indexed text of the ward's 15 June 2025 election-information page publishes the annex address as 荒川区荒川二丁目25番3号, but direct retrieval now returns HTTP 404; it is retained only as a dated indexed corroboration, not a currently accessible facility guide or evidence of a sensor floor or continued sensor operation there. The source inventory's 2021 page date is not treated as an individual post-move instrument inspection. Facility/address fields remain blank pending a current installation statement or explicit retained-meter/transfer evidence; neither the former center address nor the main office's different address is assigned by guesswork. No coordinates, proximity, source-printed seven-digit JMA-code crosswalk, physical inspection or completed sensor-move date is asserted. Approved station names, coordinates and previous provenance remain unchanged.`;
for (const url of [
  'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_tocho.html',
  'https://www.city.arakawa.tokyo.jp/documents/1728/h30bosai.pdf',
  'https://www.city.arakawa.tokyo.jp/documents/33703/kihonhousin.pdf',
  'https://www.city.arakawa.tokyo.jp/a051/gikaisenkyo/senkyo/2025togi/kouhourikkousya.html',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((station) => station.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const current = data.stations[i], old = before.stations[i];
  assert.equal(current.code, old.code);
  if (current.code !== code) assert.deepEqual(current, old);
  else {
    assert.deepEqual(Object.keys(current), Object.keys(old));
    for (const key of Object.keys(old)) if (!['note', 'sourceUrls'].includes(key)) assert.deepEqual(current[key], old[key]);
    assert.ok(current.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(current.sourceUrls.includes(url));
  }
  assert.equal(current.placementPrecision === 'exact_address', Boolean(current.publishedAddressJa), `Address/precision mismatch ${current.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Recorded' : 'Would record'} Arakawa former-center continuity evidence only; no placement, name, coordinate, record-order or coverage change; all existing provenance preserved and zero address/precision mismatches.`);
