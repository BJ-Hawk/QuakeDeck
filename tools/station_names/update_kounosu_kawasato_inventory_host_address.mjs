import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  'A6EA1B7DDE7158D0FAECF60D27B03D4A2B55407353789453DD36AB765A4E9A2A',
  'Source changed since the researched checkpoint; inspect before applying');
const data = JSON.parse(original);
const before = structuredClone(data);
const code = '1121733';
const row = data.stations.find((r) => r.code === code);
assert.ok(row);
assert.equal(row.nameJa, '鴻巣市川里');
assert.equal(row.approvedEnglishName, 'Konosu Kawasato');
assert.equal(row.providerJa, '地方公共団体');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
const address = '埼玉県鴻巣市広田3141-1';
Object.assign(row, {
  facilityNameJa: '鴻巣市川里支所',
  facilityNameEn: 'Konosu City Office, Kawasato Branch',
  publishedAddressJa: address,
  placementLocalityJa: address,
  placementPrecision: 'exact_address',
  metadataStatus: 'Public university installation inventory, official host-site address and qualified construction history',
});
row.note += ' Placement evidence checked on 17 October 2026: the University of Tokyo Earthquake Research Institute public SK-net Saitama inventory, heading last updated 16 May 2026, explicitly lists SIT.082 with municipality/station locality 鴻巣市川里 and installation facility 鴻巣市川里支所. The complete selected row and column headings were read publicly without registration or waveform access. The official city guide to かわさと館 explicitly identifies Kawasato Branch as one of its constituent facilities and independently publishes 鴻巣市広田3141-1; this named host-site address is recorded, not a room, floor, new-building wing or precise sensor enclosure. Construction history was checked rather than treating an office move as an instrument move: the 9 September 2014 Citizens and Environment Standing Committee minutes, complete PDF page 42 / printed page 40, were text-read, rendered and visually inspected. The Kawasato Branch chief describes a temporary office move from July through December 2013 during construction of the then-current branch. The passage does not mention the earthquake instrument or prove its transfer. Independently, the official disaster-plan appendix preserves the 8 May 1998 mutual-assistance agreement, complete PDF page 36 / printed page 28, also text-read, rendered and visually inspected: the former Kawasato village signs at 大字広田3141番地1. Thus both historical and current official office-address records use the same host-site parcel; no different permanent destination is substituted from the temporary-office discussion. The university inventory is direct published host evidence, while its page date is not an individual physical inspection or a full operational-continuity recertification. No uninterrupted operation during construction, sensor-transfer date, source-printed seven-digit JMA-code crosswalk, physical visit or current enclosure position is asserted. Association uses the explicit inventory locality and named facility, not coordinates, proximity or a generic municipal-office policy. The English facility name is descriptive; approved station names, coordinates and prior provenance remain unchanged.';
for (const url of [
  'http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html',
  'https://www.city.kounosu.saitama.jp/page/1555.html',
  'https://www.city.kounosu.saitama.jp/uploaded/attachment/4053.pdf',
  'https://www.city.kounosu.saitama.jp/uploaded/attachment/11217.pdf',
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
console.log(`${apply ? 'Applied' : 'Would apply'} Kawasato Branch host-site address with qualified construction history; protected fields, unrelated rows, order and prior provenance preserved; zero address/precision mismatches. Coverage delta only: exact +1, municipality/ward -1, remaining facility/address research -1; facility-only unchanged.`);
