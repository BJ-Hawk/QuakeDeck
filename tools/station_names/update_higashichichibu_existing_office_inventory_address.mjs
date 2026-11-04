import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1136930';
const row = data.stations.find((r) => r.code === code);
assert.ok(row);
assert.equal(row.nameJa, '東秩父村御堂');
assert.equal(row.approvedEnglishName, 'Mido');
assert.equal(row.providerJa, '地方公共団体');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
const address = '埼玉県秩父郡東秩父村大字御堂634番地';
Object.assign(row, { facilityNameJa: '東秩父村役場', facilityNameEn: 'Higashichichibu Village Office',
  publishedAddressJa: address, placementLocalityJa: address, placementPrecision: 'exact_address',
  metadataStatus: 'Public university installation inventory (2026), official existing office address and qualified future construction context' });
row.note += ' Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Saitama installation inventory, heading last updated 16 May 2026, explicitly names 東秩父村役場 as the installation facility for SIT.067. The complete selected row and column headings were read without logging in or retrieving restricted waveform data. The official village-office access guide independently publishes the existing office site address as 埼玉県秩父郡東秩父村大字御堂634番地, also repeated in the current official construction-progress page. This is the documented Village Office host-site address, not an assignment to the uncompleted replacement building or to a particular sensor enclosure, room, floor or wing. Construction context was independently checked: the February 2026 village newsletter, complete printed/PDF page 13, was text-read, rendered and visually inspected. It schedules new-building construction through 30 June 2027, a targeted September 2027 opening after office relocation, and subsequent old-building demolition/external works through 31 August 2028. These are planned phases, not sensor-transfer completion dates. The official construction-progress page reports steel-frame work beginning in August 2026 and continuing into September with October completion planned for that work, not completion of the whole building. The previously indexed mechanical drawing was not recovered/inspected and is not used to certify an instrument move. Association uses the explicit installation host and independently published office locality, not coordinates, proximity or a generic village-office installation policy. The inventory page date is provenance, not an individual physical instrument inspection or full operational-continuity recertification. Recheck sensor continuity when the replacement office opens; no completed transfer, future host parcel, physical visit or source-printed seven-digit JMA-code crosswalk is asserted. The English facility name is descriptive; approved station names, coordinates and earlier provenance remain unchanged.';
for (const url of [
  'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html',
  'https://www.vill.higashichichibu.saitama.jp/soshiki/01/muraaccess.html',
  'https://www.vill.higashichichibu.saitama.jp/uploaded/attachment/6007.pdf',
  'https://www.vill.higashichichibu.saitama.jp/site/sintyousyakensetu/sintyousya-kouji-shintyoku.html',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((r) => r.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const r = data.stations[i], old = before.stations[i];
  assert.equal(r.code, old.code);
  if (r.code !== code) assert.deepEqual(r, old);
  else {
    assert.deepEqual(Object.keys(r), Object.keys(old));
    for (const key of Object.keys(old)) if (!allowed.has(key)) assert.deepEqual(r[key], old[key]);
    assert.ok(r.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(r.sourceUrls.includes(url));
  }
  assert.equal(r.placementPrecision === 'exact_address', Boolean(r.publishedAddressJa), `Address/precision mismatch ${r.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} existing Higashichichibu Village Office host-site address with future construction/transfer qualification; protected fields, unrelated rows, order and provenance preserved; zero address/precision mismatches. Coverage delta only: exact +1, municipality/ward -1, remaining facility/address research -1; facility-only unchanged.`);
