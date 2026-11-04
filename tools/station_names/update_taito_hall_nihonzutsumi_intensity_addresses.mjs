import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const plan = 'https://www.city.taito.lg.jp/bosai/houshin/bosaikaigi/chikibou_r6.files/r6chiikibou00.pdf';
const planIndex = 'https://www.city.taito.lg.jp/bosai/houshin/bosaikaigi/chikibou_r6.html';
const updates = [
  {
    code: '1310632', nameJa: '東京台東区東上野', approvedEnglishName: 'Higashiueno',
    facilityNameJa: '台東区役所本庁舎（敷地内）',
    facilityNameEn: 'Taito City Office main building grounds',
    publishedAddressJa: '東京都台東区東上野4丁目5番6号',
    addressSource: 'https://www.city.taito.lg.jp/kusei/shisetsu/kuyakusho/chosha.html',
    evidence: 'The adopted Taito fiscal 2024 disaster plan, Part 2, volume 8, chapter 5, printed page 273 / PDF page 295, explicitly states that the ward has installed its seismometer within the main office grounds and supplies intensity information through the metropolitan network. The current ward-office facility guide, updated 1 April 2026, independently identifies the main office and publishes 台東区東上野4丁目5番6号. These direct host and address statements identify the Higashiueno reporting locality; the address is the published host-facility address, not a surveyed sensor position.',
  },
  {
    code: '1310650', nameJa: '東京台東区千束', approvedEnglishName: 'Senzoku',
    facilityNameJa: '東京消防庁日本堤消防署',
    facilityNameEn: 'Nihonzutsumi Fire Station',
    publishedAddressJa: '東京都台東区千束四丁目一番一号',
    addressSource: 'https://www.reiki.metro.tokyo.lg.jp/reiki/reiki_honbun/g101RG00002227.html',
    evidence: 'The adopted Taito fiscal 2024 disaster plan, Part 2, volume 8, chapter 5, printed page 273 / PDF page 295, separately names the Tokyo Fire Department seismometer installed at 日本堤消防署. The current Tokyo metropolitan fire-department establishment ordinance, appended table row 東京消防庁日本堤消防署, independently publishes 台東区千束四丁目一番一号 as its location. The named host and published address identify the Senzoku reporting locality; this is not an inference from the station jurisdiction or another nearby fire station.',
  },
];
for (const update of updates) {
  const row = data.stations.find((station) => station.code === update.code);
  assert.ok(row, `Missing ${update.code}`);
  assert.equal(row.nameJa, update.nameJa);
  assert.equal(row.approvedEnglishName, update.approvedEnglishName);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa, `Existing placement ${update.code}`);
  Object.assign(row, {
    facilityNameJa: update.facilityNameJa,
    facilityNameEn: update.facilityNameEn,
    publishedAddressJa: update.publishedAddressJa,
    placementLocalityJa: update.publishedAddressJa,
    placementPrecision: 'exact_address',
    metadataStatus: 'Direct official instrument-host statement and independently published facility address',
  });
  row.note += ` Placement evidence checked on 17 October 2026: ${update.evidence} The complete relevant plan page was text-read, rendered and visually inspected, including its section headings and surrounding paragraphs. The official plan publication page identifies this as the adopted fiscal 2024 revision, not a draft. No source is claimed to print the seven-digit station code; the host is matched through the explicit installation statement and its published locality. No particular floor, room, exact position within the site, new installation date or physical site inspection is asserted. The English facility label is descriptive, not claimed as an official English title. Approved Japanese and English station names, coordinates and previous provenance are preserved.`;
  for (const url of [planIndex, plan, update.addressSource]) {
    if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
  }
}

const codes = new Set(updates.map((update) => update.code));
const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((station) => station.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  if (!codes.has(row.code)) assert.deepEqual(row, old);
  else {
    assert.deepEqual(Object.keys(row), Object.keys(old));
    for (const key of Object.keys(old)) if (!allowed.has(key)) assert.deepEqual(row[key], old[key]);
    assert.ok(row.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} two Taito host-address updates; all protected fields, other rows, order and previous provenance preserved; zero address/precision mismatches. Coverage delta only: exact +2, municipality/ward -2, remaining facility/address research -2; facility-only unchanged.`);
