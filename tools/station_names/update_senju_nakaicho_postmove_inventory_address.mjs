import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  'DB8D6291D63F8B8DBAB628E7393BBA222CAD39B498E25B16EBF37FF63DBAB2CC');
const data = JSON.parse(original), before = structuredClone(data);
const row = data.stations.find((r) => r.code === '1312153');
assert.ok(row);
assert.equal(row.nameJa, '東京足立区千住中居町');
assert.equal(row.approvedEnglishName, 'Senju Nakaicho');
assert.equal(row.providerJa, '地方公共団体');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.publishedAddressJa && !row.facilityNameJa && !row.facilityNameEn);

const address = '東京都足立区千住中居町9-14';
Object.assign(row, {
  publishedAddressJa: address,
  facilityNameJa: '千住消防署',
  facilityNameEn: 'Senju Fire Station',
  placementLocalityJa: address,
  placementPrecision: 'exact_address',
  metadataStatus: 'Dated public installation-host inventory and independent current metropolitan facility-address directory',
});
row.note += ' Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Tokyo Fire Department observation-point inventory, heading last updated 11 January 2022, explicitly names 千住消防署 in the installation-facility column for TFD 0139080, with former observation-point identifier 0560610 before 21 February 2012. The complete selected row and column headings were read publicly without registration or restricted waveform access. The observation-point identifier date is not interpreted as a physical instrument-transfer date. Tokyo Metropolitan Government current police/fire directory independently publishes the named Senju Fire Station at 足立区千住中居町9-14, with telephone 03-3882-0119; its complete named address row was read. The metropolitan assembly police/fire committee record for 2009, No. 10, explicitly describes construction on a different parcel at 千住中居町九番十四号, with planned office commencement on 26 February 2010. That office-opening plan is not sensor-transfer evidence. Unlike the previously inspected August 2005 ward newsletter, the January 2022 published installation inventory postdates the known 2010 facility move and supplies an individual named installation host; the stored address uses that later inventory plus the independently published current address, not the old newsletter alone or an inference that office relocation automatically moves an instrument. This is the named host-facility site address, not a surveyed enclosure/floor, proof of the precise installed sensor position, a source-printed seven-digit JMA-code crosswalk or a fresh 2026 instrument/operational-continuity inspection. The dated inventory does not recertify each row as newly inspected on its heading date; its source-age limitation is retained. Association uses the explicit installation-facility name and its published Senju Nakaicho address, not catalogue/provider coordinates, marker proximity, response jurisdiction or a generic fire-station installation policy. The English facility name is descriptive; approved station names, coordinates and prior provenance remain unchanged.';
for (const url of [
  'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html',
  'https://www.metro.tokyo.lg.jp/tosei/iken-sodan/sodan/kesatsu',
  'https://www.gikai.metro.tokyo.lg.jp/record/police-fire-fighting/2009-10.html',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

const allowed = new Set(['publishedAddressJa', 'facilityNameJa', 'facilityNameEn',
  'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'sourceUrls', 'note']);
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
    for (const key of Object.keys(old)) if (!allowed.has(key)) assert.deepEqual(r[key], old[key]);
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
console.log(`${apply ? 'Applied' : 'Would apply'} Senju Nakaicho / Senju Fire Station host address; protected fields, unrelated rows, order and prior provenance preserved; zero address/precision mismatches. Coverage delta only: exact +1, municipality/ward -1, remaining facility/address research -1; facility-only unchanged.`);
