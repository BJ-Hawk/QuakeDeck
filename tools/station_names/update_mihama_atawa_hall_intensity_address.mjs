import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '2456131';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '三重御浜町阿田和');
assert.equal(station.municipalityCode, '2456100');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(station.approvedEnglishName, 'Mihama Atawa');
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '2456100').map((row) => row.code), ['2456100', '2456131']);
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '御浜町役場',
  facilityNameEn: 'Mihama Town Hall',
  publishedAddressJa: '三重県南牟婁郡御浜町大字阿田和6120番地1',
  placementLocalityJa: '南牟婁郡御浜町大字阿田和6120番地1',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct current-linked municipal disaster-plan Town Hall meter statement and separate official named facility address',
});
station.note += ' The official municipal disaster-information index 177.html, updated 4 June 2026, currently links jisintunamibousai.pdf. Its cover identifies the March 2024 revised earthquake/tsunami plan, not a draft. Complete printed page 84 / PDF page 90, Chapter 1 Section 1, Town Disaster Headquarters installation criteria, explicitly states that town intensity is from the Town Hall intensity meter (町の震度は、町役場の震度計による). The cover and complete relevant page were downloaded, text-read and rendered for visual inspection. This is a direct instrument-host statement, not a host inferred from a disaster-headquarters room. The separate official single-page election-register information-file inventory 011003.pdf, dated 1 April 2023, explicitly pairs 御浜町役場 with 三重県南牟婁郡御浜町大字阿田和6120番地1 in its organization-name/location row; the complete page was text-read and visually inspected. The current official Town Hall information page independently publishes the same named hall and address. Composite matching uses the explicit hall meter, Atawa locality and the two bundled town stations: the separate JMA station 2456100 is explicitly Teradani General Park and remains unchanged. No coordinates, proximity, Street View or generic municipality-only host assumption were used. A councillor-blog search preview was a discovery lead only; its purported 2025 meeting details were not used as verified placement evidence. No source is claimed to print the seven-digit station code, establish a fresh physical inspection or an exact sensor installation/transfer date, or identify a room, floor, wing or precise within-site sensor position. The English facility label is descriptive; the approved Mihama Atawa station name and Japanese station name remain unchanged.';
for (const url of [
  'https://www.town.mihama.mie.jp/soshikikarasagasu/somuka/kurashinojoho/3/177.html',
  'https://www.town.mihama.mie.jp/material/files/group/14/jisintunamibousai.pdf',
  'https://www.town.mihama.mie.jp/material/files/group/2/011003.pdf',
  'https://www.town.mihama.mie.jp/gyoseijoho/mihamachoyakubanitsuite/index.html',
]) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);

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
    assert.ok(row.note.startsWith(old.note));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Mihama Town Hall exact address; protected-field and address/precision checks passed.`);
