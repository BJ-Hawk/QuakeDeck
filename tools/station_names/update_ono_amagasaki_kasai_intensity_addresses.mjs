import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const updates = [
  {
    code: '2821832', nameJa: '小野市王子町',
    facilityNameJa: '小野市防災センター', facilityNameEn: 'Ono City Disaster Prevention Center',
    publishedAddressJa: '兵庫県小野市王子町809番地',
    urls: [
      'https://www.city.ono.hyogo.jp/soshikikarasagasu/somubu_shiminserviceka/gyomuannai/6/2/2/1/8941.html',
      'https://www.city.ono.hyogo.jp/material/files/group/9/ONOPress202209.pdf',
      'https://www.city.ono.hyogo.jp/soshikikarasagasu/shiminanzembu_bosaigroup/6/2168.html',
    ],
    evidence: 'Ono City official newsletter, September 2022, printed page 4 (the right side of the pages 4-5 spread on PDF page 3), explicitly identifies the city intensity meter as installed at Ono City Disaster Prevention Center and says its observations are transmitted through the prefecture to JMA for publication as Ono intensity. The complete spread was text-read and visually inspected, including the host statement and instrument photograph; the newsletter archive independently identifies this issue and its earthquake-information feature. The municipal facility profile, updated 27 March 2022 and still publicly available, directly identifies 小野市防災センター, gives 王子町809番地 as its location, and publishes the full Japanese address in the responsible facility department contact. The separate main City Hall address 中島町531 in the page footer is not used. This is a dated direct municipal host statement, not a fresh 2026 instrument inspection.',
  },
  {
    code: '2820230', nameJa: '尼崎市昭和通',
    facilityNameJa: '尼崎市防災センター', facilityNameEn: 'Amagasaki City Disaster Prevention Center',
    publishedAddressJa: '兵庫県尼崎市昭和通2丁目6番75号',
    urls: [
      'https://www.city.amagasaki.hyogo.jp/_res/projects/default_project/_page_/001/033/824/R60105.pdf',
      'https://www.city.amagasaki.hyogo.jp/map/1000375/1000389/1000459.html',
    ],
    evidence: 'Amagasaki City fiscal 2023 policy-promotion meeting No. 13 report, meeting of 5 January 2024, PDF page 3, records the fire bureau director\'s explicit statement that the meter installed at Disaster Prevention Center supplies the intensity published by JMA as Amagasaki intensity. The report describes the actual Shindo 3 observation during the 1 January 2024 Noto Peninsula earthquake, not merely a proposed installation or a question. The cover and complete relevant page were text-read, and the host-statement page was visually inspected. The city\'s Disaster Prevention Center facility guide, updated 20 February 2025, directly identifies the host and publishes 兵庫県尼崎市昭和通2丁目6-75; its official postal contact gives 兵庫県尼崎市昭和通2丁目6番75号. The department\'s fourth-floor contact is not represented as the meter floor. The obsolete R6honpen_2.pdf plan link returned 404 and was not used as inspected placement evidence. The 2024 operational statement is not represented as a fresh 2026 instrument inspection.',
  },
  {
    code: '2822030', nameJa: '加西市北条町',
    facilityNameJa: '加西市役所', facilityNameEn: 'Kasai City Hall',
    publishedAddressJa: '兵庫県加西市北条町横尾1000番地',
    urls: [
      'https://www.city.kasai.hyogo.jp/site/keikaku-sesaku/2012.html',
      'https://www.city.kasai.hyogo.jp/uploaded/attachment/32917.pdf',
      'https://www.city.kasai.hyogo.jp/map/25052.html',
    ],
    evidence: 'Kasai City regional disaster plan, fiscal 2025 revision linked from the official plan index updated 27 March 2026, earthquake-response volume, chapter 2 section 13, printed page 27 / PDF page 33 of 32917.pdf, explicitly lists 市役所 as the location of a 計測震度計 connected to Hyogo Prefecture by telemetry. The same table separately identifies the JMA Shimomanganjicho and Kyoto University Kochicho research instruments; neither is substituted for the prefectural City Hall instrument. The cover confirms fiscal 2025, and the complete installation-table page was text-read and visually inspected. The official City Hall facility guide directly publishes 兵庫県加西市北条町横尾1000番地. The index update date is not asserted as an instrument installation, transfer, or inspection date.',
  },
];

for (const update of updates) {
  const s = data.stations.find((row) => row.code === update.code);
  assert.ok(s, `Missing station ${update.code}`);
  assert.equal(s.nameJa, update.nameJa);
  assert.equal(s.providerJa, '地方公共団体');
  assert.equal(s.placementPrecision, 'municipality_or_ward');
  assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa, `Existing placement ${s.code}`);
  Object.assign(s, {
    facilityNameJa: update.facilityNameJa,
    facilityNameEn: update.facilityNameEn,
    publishedAddressJa: update.publishedAddressJa,
    placementLocalityJa: update.publishedAddressJa.replace(/^兵庫県/, ''),
    placementPrecision: 'exact_address',
    metadataStatus: 'Direct official municipal instrument-host statement and published host-facility address',
    note: `${update.evidence} Neither source is claimed to publish the seven-digit JMA code. The host is supported by the explicit instrument statement, not proximity, catalogue coordinates, or a locality-only guess. No current room, floor, or precise within-site meter position is asserted. The English facility label is descriptive, not claimed as an official English title; approved English and Japanese station names remain unchanged.`,
  });
  for (const url of update.urls) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
}

const targets = new Set(updates.map((u) => u.code));
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Ono/Amagasaki/Kasai host-address updates; protected-field and address/precision checks passed.`);
