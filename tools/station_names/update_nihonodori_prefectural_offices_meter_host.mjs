import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1410442';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '横浜中区日本大通');
assert.equal(station.municipalityCode, '1410400');
assert.equal(station.approvedEnglishName, 'Nihonodori');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '神奈川県庁',
  facilityNameEn: 'Kanagawa Prefectural Government Offices',
  metadataStatus: 'Direct current prefectural network meter-host identity; exact host-building or sensor parcel address not established',
});
station.note += ' The current Kanagawa earthquake-plan manual index jishin4.html, updated 20 August 2026, links j4-1.pdf. The complete printed page 743 / PDF page 1, 資料4-1-(1) 神奈川県震度情報ネットワークシステム概要図, explicitly lists 県庁 in the 県設置震度計（24地点） box. The complete page was downloaded, text-read and rendered for visual inspection. The separate 県庁西庁舎 label in the central server box identifies the intensity-processing server, not necessarily the ground-sensor building; that server label is not promoted into a more specific meter host. The official prefectural access guide, updated 1 September 2025, identifies the government offices and publishes their general 日本大通1 contact/access address while separately linking main, new, east and west buildings. Composite matching pairs the explicitly listed prefectural government meter with the Nihonodori reporting locality; the other three bundled Naka Ward stations are separately identified at the meteorological observatory, Naka Public Works Office and Naka Fire Station. No coordinates or proximity were used. Facility identity is recorded at the government-office-complex level only. The general access/contact address is not asserted as the exact sensor parcel or a particular host building without installation evidence, so publishedAddressJa remains blank and locality precision is unchanged. No source is claimed to print the seven-digit code, prove a fresh physical inspection, exact installation/transfer date, room, floor or within-site position. The English facility label is descriptive; the approved Nihonodori name and Japanese station name remain unchanged.';
for (const url of [
  'https://www.pref.kanagawa.jp/docs/j8g/cnt/f5150/documents/jishin4.html',
  'https://www.pref.kanagawa.jp/documents/85497/j4-1.pdf',
  'https://www.pref.kanagawa.jp/access/index.html',
]) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);

const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'metadataStatus', 'note', 'sourceUrls']);
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} prefectural-government meter facility only; address and precision unchanged, protected-field checks passed.`);
