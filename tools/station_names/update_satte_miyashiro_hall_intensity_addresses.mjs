import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const survey = 'https://www.data.jma.go.jp/kumagaya/shosai/topics/2011/20110318.pdf';
const updates = [
  {
    code: '1124030', nameJa: '幸手市東', facilityNameJa: '幸手市役所',
    facilityNameEn: 'Satte City Hall grounds',
    publishedAddressJa: '埼玉県幸手市東4丁目6番8号',
    sources: [survey, 'https://www.city.satte.lg.jp/section/reikisyu/reiki_honbun/e341RG00000105.html', 'https://www.city.satte.lg.jp/soshiki/keiyaku/chousya/index.html'],
    note: 'JMA Kumagaya Local Meteorological Office field-survey release dated 18 March 2011, 20110318.pdf, PDF page 1, explicitly identifies Satte City Hall grounds as the intensity observation-point installation and names the surveyed municipal reporting station 幸手市東. The complete cover/summary page was text-read and directly downloaded for visual inspection after the web screenshot failed. The report distinguishes ground subsidence on the grounds from the inspected intensity-meter base, for which no tilt or cracks were found; no inference from surrounding damage is needed to establish the host. The municipal hall-management rules, article 2 paragraph 2, explicitly locate City Hall at 幸手市東四丁目6番8号, and the current official hall guide independently publishes the full Saitama-prefecture address. The historical field survey is not represented as a fresh 2026 meter inspection. Neither the hall guide nor the survey is claimed to publish the seven-digit station code. No current hall wing, room, floor, exact within-site position, installation date or transfer date is asserted. English facility label is descriptive; approved station names remain unchanged.',
  },
  {
    code: '1144231', nameJa: '宮代町笠原', facilityNameJa: '宮代町役場',
    facilityNameEn: 'Miyashiro Town Hall grounds',
    publishedAddressJa: '埼玉県南埼玉郡宮代町笠原1丁目4番1号',
    sources: [survey, 'https://www.town.miyashiro.lg.jp/0000000646.html', 'https://www.town.miyashiro.lg.jp/0000001966.html'],
    note: 'JMA Kumagaya Local Meteorological Office field-survey release dated 18 March 2011, 20110318.pdf, PDF page 1, explicitly identifies Miyashiro Town Hall grounds as the intensity observation-point installation in Kasahara and names the surveyed municipal reporting station 宮代町笠原. The complete cover/summary page was text-read and directly downloaded for visual inspection after the web screenshot failed. The report distinguishes ground subsidence on the grounds from the inspected intensity-meter base, for which no tilt or cracks were found; no inference from surrounding damage is needed to establish the host. The official residential-address guide, updated 17 September 2026, explicitly gives the full Town Hall address as 埼玉県南埼玉郡宮代町笠原1丁目4番1号; the official administrative profile, updated 9 June 2026, independently confirms the hall address. The historical field survey is not represented as a fresh 2026 meter inspection. Neither the address guides nor the survey is claimed to publish the seven-digit station code. No current room, floor, exact within-site position, installation date or transfer date is asserted. English facility label is descriptive; approved station names remain unchanged.',
  },
];

for (const update of updates) {
  const s = data.stations.find((row) => row.code === update.code);
  assert.ok(s);
  assert.equal(s.nameJa, update.nameJa);
  assert.equal(s.providerJa, '地方公共団体');
  assert.equal(s.placementPrecision, 'municipality_or_ward');
  assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
  Object.assign(s, {
    facilityNameJa: update.facilityNameJa, facilityNameEn: update.facilityNameEn,
    publishedAddressJa: update.publishedAddressJa,
    placementLocalityJa: update.publishedAddressJa.replace(/^埼玉県/, ''),
    placementPrecision: 'exact_address',
    metadataStatus: 'Direct JMA field-survey host identification and official published facility address',
    note: update.note,
  });
  for (const url of update.sources) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
}

const targets = new Set(updates.map((row) => row.code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((row) => row.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  if (!targets.has(row.code)) assert.deepEqual(row, old);
  else {
    for (const key of new Set([...Object.keys(row), ...Object.keys(old)])) if (!allowed.has(key)) assert.deepEqual(row[key], old[key]);
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Satte/Miyashiro hall-ground addresses; protected-field and address/precision checks passed.`);
