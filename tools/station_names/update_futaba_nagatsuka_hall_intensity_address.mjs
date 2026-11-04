import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '0754633';
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '双葉町長塚');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '双葉町役場庁舎',
  facilityNameEn: 'Futaba Town Hall',
  publishedAddressJa: '福島県双葉郡双葉町大字長塚字町西73番地4',
  placementLocalityJa: '双葉町大字長塚字町西73番地4',
  placementPrecision: 'exact_address',
  metadataStatus: 'Official instrument-relocation destination, JMA relocated-station code, and current municipal facility address',
  note: 'Fukushima Prefecture contract 21-05010-0006, 510240.pdf, both pages visually inspected in full, explicitly describes relocating the intensity-network equipment from Morotake Community Hall to the temporary Town Hall as part of the municipal office move; the work site is Nagatsuka Machinishi. The contract was signed on 10 February 2022; its scheduled completion is not asserted as an actual transfer date. JMA notice dated 24 October 2022, cover and complete PDF page 3 relocation table read and table visually inspected, explicitly replaces Morotake code 0754632 with Nagatsuka code 0754633 under relocated observation points, effective for code parameters on 24 November 2022 at 12:00. The March 2025 municipal administrative-facility plan, cover/context and complete printed/PDF page 6 read and the facility table visually inspected, identifies the 2022 Town Hall at 長塚字町西73番地4 and confirms the return of the main municipal function there. The current official homepage independently publishes its full Fukushima-prefecture address. These sources jointly establish the host/address mapping; the XML table alone contains no street address. No fresh 2026 meter inspection, exact physical transfer date, current room, floor, or within-site position is claimed. The former Morotake and off-site Iwaki branch addresses are not used. The English facility label is descriptive; approved station names remain unchanged.',
});
for (const url of [
  'https://www.pref.fukushima.lg.jp/uploaded/attachment/510240.pdf',
  'https://xml.kishou.go.jp/20221024_jmaxml_info.pdf',
  'https://www.town.fukushima-futaba.lg.jp/secure/16750/07.pdf',
  'https://www.town.fukushima-futaba.lg.jp/',
]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);

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
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Futaba Town Hall host-address update; protected-field and address/precision checks passed.`);
