import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const updates = [
  ['1312051', '東京練馬区東大泉', '石神井消防署大泉出張所', 'Shakujii Fire Station, Oizumi Branch', '東京都練馬区東大泉6-34-44'],
  ['1312052', '東京練馬区光が丘', '光が丘消防署', 'Hikarigaoka Fire Station', '東京都練馬区光が丘2-9-1'],
];
const urls = [
  'https://www.call-center.jp/faq_nerima/faq.asp?faqno=NER01457',
  'https://www.city.nerima.tokyo.jp/',
];
for (const [code, nameJa, facilityNameJa, facilityNameEn, publishedAddressJa] of updates) {
  const s = data.stations.find((row) => row.code === code);
  assert.ok(s, `Missing station ${code}`);
  assert.equal(s.nameJa, nameJa);
  assert.equal(s.providerJa, '地方公共団体');
  assert.equal(s.placementPrecision, 'municipality_or_ward');
  assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa, `Existing placement ${code}`);
  Object.assign(s, {
    facilityNameJa,
    facilityNameEn,
    publishedAddressJa,
    placementLocalityJa: publishedAddressJa.replace(/^東京都/, ''),
    placementPrecision: 'exact_address',
    metadataStatus: 'Official ward FAQ directly identifies instrument hosts and publishes their addresses',
    note: `Nerima Ward's official FAQ question 1457, 練馬区の震度は、どこで計測しているのか。, created and updated 4 February 2026, explicitly states that ward intensity is measured by meters at Nerima Ward Office (豊玉北6-12-1), Shakujii Fire Station Oizumi Branch (東大泉6-34-44), and Hikarigaoka Fire Station (光が丘2-9-1). This directly identifies ${facilityNameJa} and publishes its street address, ${publishedAddressJa}. Nerima Ward's official homepage links to the call-center.jp/faq_nerima service; the FAQ is ward-authored public information on an externally hosted service, not an independent third-party address directory. The two fire-service host addresses distinguish the 東大泉 and 光が丘 reporting stations from the already-addressed ward-office station. The FAQ separately describes Tokyo Gas's denser private instrument network; none of those private sensors is substituted for this reporting station. The Tokyo prefectural prefix is included as the address jurisdiction. No placement is inferred from coordinates, proximity, or locality alone. No current room, floor, or within-site meter position is claimed. The English facility label is descriptive, not asserted to be an official English title; approved English and Japanese station names are unchanged.`,
  });
  for (const url of urls) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
}
const targets = new Set(updates.map(([code]) => code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((s) => s.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const s = data.stations[i];
  const old = before.stations[i];
  assert.equal(s.code, old.code);
  if (!targets.has(s.code)) assert.deepEqual(s, old);
  else {
    for (const key of new Set([...Object.keys(s), ...Object.keys(old)])) if (!allowed.has(key)) assert.deepEqual(s[key], old[key]);
    for (const url of old.sourceUrls) assert.ok(s.sourceUrls.includes(url));
  }
  assert.equal(s.placementPrecision === 'exact_address', Boolean(s.publishedAddressJa), `Address/precision mismatch ${s.code}`);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Nerima fire-host address updates; protected-field and address/precision checks passed.`);
