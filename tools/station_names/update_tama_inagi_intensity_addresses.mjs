import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const tamaPlan = 'https://www.city.tama.lg.jp/_res/projects/default_project/_page_/001/004/801/r7.8.pdf';
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '1322500').map((row) => row.code), ['1322530']);
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '1322400').map((row) => row.code).sort(), ['1322431', '1322450']);
const updates = [
  {
    code: '1322431', nameJa: '多摩市関戸', facilityNameJa: '多摩市役所',
    facilityNameEn: 'Tama City Hall', publishedAddressJa: '東京都多摩市関戸六丁目12番地1',
    sources: [tamaPlan, 'https://www.city.tama.lg.jp/map/shiyakusho/shisetsu/1004551.html', 'https://www.city.tama.lg.jp/shisei/keikaku/tatekae/1021298.html'],
    note: 'Tama disaster plan, adopted August 2025 revision r7.8.pdf, printed page 177 / PDF page 192, section 6, explicitly places a seismometer on City Hall grounds and distinguishes its data supply to the metropolitan government from the fire-service network. The cover and complete relevant page were read and the host page visually inspected; the earlier draft was not used. The official City Hall guide publishes 関戸6丁目12番地1 and supplies the full Tokyo-prefecture address in its contact block. The 25 September 2026 reconstruction update describes basic-design work, not a completed hall or instrument move. Composite matching identifies the Sekido reporting locality from the explicit host and published address, not proximity. The following section places the disaster-control room on the second floor, not the meter. No current room, floor, precise within-site position, transfer date or fresh meter inspection is asserted. English facility label is descriptive; approved station names remain unchanged.',
  },
  {
    code: '1322450', nameJa: '多摩市鶴牧', facilityNameJa: '多摩消防署多摩センター出張所',
    facilityNameEn: 'Tama Fire Station Tama Center Branch', publishedAddressJa: '東京都多摩市鶴牧一丁目27番1号',
    sources: [tamaPlan, 'https://www.tfd.metro.tokyo.lg.jp/fs/tama/index.html', 'https://www.city.tama.lg.jp/kurashi/shobo/1008226.html'],
    note: 'Tama disaster plan, adopted August 2025 revision r7.8.pdf, printed page 177 / PDF page 192, section 6, explicitly identifies the operational network seismometer at Tama Fire Station Tama Center Branch, separately from City Hall. The cover and complete relevant page were read and the host page visually inspected; the earlier draft was not used. The current Tokyo Fire Department access guide directly publishes 多摩市鶴牧一丁目27番1号 for this branch, not the main station in Suwa. The municipal fire-facility list independently publishes the full Tokyo-prefecture address as 鶴牧1-27-1 in indexed official text; direct page retrieval omitted its dynamic facility rows. Composite matching pairs the documented branch instrument with the Tsurumaki reporting locality; the plan does not publish a seven-digit JMA code. No current room, floor, precise within-site position, transfer date or fresh meter inspection is asserted. English facility label is descriptive; approved station names remain unchanged.',
  },
  {
    code: '1322530', nameJa: '稲城市東長沼', facilityNameJa: '稲城消防署',
    facilityNameEn: 'Inagi Fire Station', publishedAddressJa: '東京都稲城市東長沼2111番地',
    sources: ['https://www.city.inagi.tokyo.jp/en/_res/projects/default_project/_page_/001/012/952/r7.3kessan_jimuhokoku.pdf', 'https://www.city.inagi.tokyo.jp/shisetsu/anzen/1005028/1005029.html'],
    note: 'Inagi fiscal 2024 administrative report, r7.3kessan_jimuhokoku.pdf, printed page 195 / PDF page 196, section 5 commissioned services, explicitly identifies the seismometer-system maintenance location as 消防署設置. The cover and complete relevant page were read and the table visually inspected. The official Fire Station guide, updated 16 May 2025, names 稲城消防署 and publishes 稲城市東長沼2111番地; its contact block independently supplies the full Tokyo-prefecture address and host identity. Composite matching pairs the explicitly documented municipal fire-station instrument with the sole bundled Inagi reporting station and its Higashinaganuma locality; neither source is claimed to publish the seven-digit JMA code. This does not assign City Hall, a fire-service branch, an instrument installation date, a current room/floor or precise within-site position. The annual maintenance record is not represented as a fresh 2026 inspection. English facility label is descriptive; approved station names remain unchanged.',
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
    placementLocalityJa: update.publishedAddressJa.replace(/^東京都/, ''),
    placementPrecision: 'exact_address',
    metadataStatus: 'Direct official instrument-host statement and published facility address',
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Tama/Inagi host-address updates; protected-field and address/precision checks passed.`);
