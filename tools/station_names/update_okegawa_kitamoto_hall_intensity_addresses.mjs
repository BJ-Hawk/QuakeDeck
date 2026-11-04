import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const updates = [
  {
    code: '1123132', nameJa: '桶川市泉', facilityNameJa: '桶川市役所',
    facilityNameEn: 'Okegawa City Hall', publishedAddressJa: '埼玉県桶川市泉1丁目3番28号',
    urls: [
      'https://www.city.okegawa.lg.jp/shisei/shinoshokaikanko/keikaku_sengen/keikaku/shinkotyunokeikaku/6113.html',
      'https://www.city.okegawa.lg.jp/material/files/group/16/hyoushipage.pdf',
      'https://www.city.okegawa.lg.jp/material/files/group/16/shinnsaitaisaku.pdf',
      'https://www.city.okegawa.lg.jp/shisei/shiyakushoshokuin/siyakusyo_annai/7483.html',
    ],
    evidence: 'Okegawa City regional disaster plan, final March 2023 revision (not the November 2022 draft), earthquake-response volume, chapter 1 section 3, printed page 260 / PDF page 8, explicitly states that the city checks intensity information from the instrumental intensity meter installed at City Hall. The cover confirms March 2023; the full relevant page was text-read and visually inspected. The city\'s current City Hall access guide, updated 8 July 2025, directly publishes 埼玉県桶川市泉1丁目3番28号. This identifies the host of the municipal Izumi observation, rather than inferring a facility from the rounded catalogue coordinates.',
  },
  {
    code: '1123331', nameJa: '北本市本町', facilityNameJa: '北本市役所',
    facilityNameEn: 'Kitamoto City Hall', publishedAddressJa: '埼玉県北本市本町1丁目111番地',
    urls: [
      'https://www.city.kitamoto.lg.jp/shisei/keikaku/anzen/1418099506315.html',
      'https://www.city.kitamoto.lg.jp/material/files/group/5/1hyousimokuji.pdf',
      'https://www.city.kitamoto.lg.jp/material/files/group/5/4oukyuutaisaku.pdf',
      'https://www.city.kitamoto.lg.jp/shisei/gaiyo/kitamotoshiyakusho/1419406036152.html',
    ],
    evidence: 'Kitamoto City regional disaster plan, adopted March 2024 revision (not the undated draft syuuseitiikibou.pdf), disaster-response volume, printed page 3-2 / PDF page 4, explicitly states that the city\'s intensity is measured by the instrumental intensity meter installed at City Hall. The complete page was text-read and visually inspected. Printed page 3-42 / PDF page 44 independently states that city intensity is obtained from the meter installed at City Hall; that full page was also text-read. The cover confirms March 2024. The current City Hall access guide, updated 1 October 2025, publishes 北本市本町1-111 and its full official postal address 埼玉県北本市本町1丁目111番地. This directly identifies the host for the municipal Honcho observation, not a placement inferred from coordinates or locality alone.',
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
    placementLocalityJa: update.publishedAddressJa.replace(/^埼玉県/, ''),
    placementPrecision: 'exact_address',
    metadataStatus: 'Official adopted municipal plan instrument-host statement and published City Hall address',
    note: `${update.evidence} Neither source is claimed to contain the seven-digit JMA station code. No current room, floor, or precise within-site meter position is asserted. The English facility label is descriptive, not claimed as an official English title; approved English and Japanese station names remain unchanged.`,
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Okegawa/Kitamoto City Hall host-address updates; protected-field and address/precision checks passed.`);
