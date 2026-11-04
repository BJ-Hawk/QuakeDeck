import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1438230';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '箱根町湯本');
assert.equal(station.municipalityCode, '1438200');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(station.approvedEnglishName, 'Yumoto');
assert.equal(data.stations.filter((row) => row.municipalityCode === '1438200').length, 1);
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '箱根町役場',
  facilityNameEn: 'Hakone Town Hall',
  publishedAddressJa: '神奈川県足柄下郡箱根町湯本256',
  placementLocalityJa: '足柄下郡箱根町湯本256',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct municipal and prefectural research-institute reporting-meter host statements and separate official facility address',
});
station.note += ' Hakone Town current-volcano guide 1100000002068/index.html, updated 20 March 2016, explicitly states in its 問い合わせ section that the town intensity published by JMA is measured by the instrumental seismic-intensity meter installed at Town Hall. Kanagawa Prefecture Hot Springs Research Institute FAQ, updated 30 January 2018, independently identifies Hakone Town Hall as the host of the meter used for JMA intensity reports; its complete question 1 and figure captions were read as HTML. The institute current Hakone-activity page, read with September/October 2026 activity content, also explicitly locates the reporting meter at 箱根町役場（箱根湯本） while distinguishing its own mountain sensors and non-JMA equivalent intensities. Its page-template update label is 10 October 2023, not a claimed new meter-inspection date. The official Town Hall facility guide 1100000002301/index.html, updated 20 March 2016, independently names 箱根町役場 and publishes 神奈川県足柄下郡箱根町湯本256 under 住所 in the facility body, not merely the website footer. The complete relevant HTML sections were read; no photograph, map proximity or coordinate inference was used. Composite matching uses the explicit reporting-meter host, Yumoto locality, and sole bundled Hakone reporting station. The separate institute observation facility at Yumoto 997 and mountain sensors are not this station. No source is claimed to print the seven-digit station code or establish a fresh physical inspection, exact sensor installation/transfer date, building wing, room, floor or within-site position. The English facility label is descriptive; the approved Yumoto English station name and Japanese station name remain unchanged.';
for (const url of [
  'https://www.town.hakone.kanagawa.jp/www/contents/1100000002068/index.html',
  'https://www.onken.odawara.kanagawa.jp/volcano-geology/faq/',
  'https://www.onken.odawara.kanagawa.jp/volcano-geology/hakone-volcanic-activity/?content_id=1',
  'https://www.town.hakone.kanagawa.jp/www/contents/1100000002301/index.html',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Hakone Town Hall exact address; protected-field and address/precision checks passed.`);
