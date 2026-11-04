import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1421831';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '綾瀬市深谷中');
assert.equal(station.municipalityCode, '1421800');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(station.approvedEnglishName, 'Fukaya Naka');
assert.equal(data.stations.filter((row) => row.municipalityCode === '1421800').length, 1);
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '綾瀬市消防本部',
  facilityNameEn: 'Ayase City Fire Headquarters',
  publishedAddressJa: '神奈川県綾瀬市深谷中1-4-30',
  placementLocalityJa: '綾瀬市深谷中1-4-30',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct adopted municipal disaster-plan existing-meter host statement and separate official fire-headquarters address',
});
station.note += ' The official municipal plan index 24888.html, updated 29 July 2026, states that the disaster council adopted the revised plan on 19 May 2026 and that it takes effect on 1 April 2027; it is not the plan currently in force on the research date. Its linked final procedures volume syuhohen.pdf, complete printed/PDF page 7, Chapter 1 Section 1, item 1-1, explicitly identifies 綾瀬市消防本部に設置された計測震度計 as an existing means of checking city intensity. This is installed-equipment wording, not a proposed meter installation or future host move. The final cover has no draft marking; the cover and entire relevant table page were text-read and rendered for visual inspection. Discovery files with 改定（案） on the cover were not used as the adopted source. The separate current official municipal fire-headquarters facility profile 13435.html, updated 1 February 2023, explicitly identifies the new fire building as operational from 7 March 2020 and publishes 綾瀬市深谷中1-4-30 in its facility-detail section. The adopted 2026 instrument statement thus postdates the new headquarters opening, rather than relying on a pre-move host reference. Composite matching uses the explicitly named installed meter, current named host/address, Fukaya Naka locality and sole bundled Ayase reporting station, not coordinates or proximity. No source is claimed to publish the seven-digit code, prove an exact physical sensor-transfer date, provide a fresh physical inspection or identify a particular wing, room, floor or within-site position. The 2027 plan commencement is not a sensor installation or relocation date. The English facility label is descriptive; the approved Fukaya Naka station name and Japanese station name remain unchanged.';
for (const url of [
  'https://www.city.ayase.kanagawa.jp/soshiki/kikikanrika/bosai_bohan_anzen_anshin/3/3/24888.html',
  'https://www.city.ayase.kanagawa.jp/material/files/group/3/syuhohen.pdf',
  'https://www.city.ayase.kanagawa.jp/soshiki/hishokohoka/locationsite/rokelibrary/publicfacility/13435.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Ayase City Fire Headquarters exact address; protected-field and address/precision checks passed.`);
