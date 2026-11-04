import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const updates = [
  ['0521430', 'にかほ市平沢', 'にかほ市役所仁賀保庁舎', 'Nikaho City Hall Nikaho Office', '秋田県にかほ市平沢字鳥ノ子渕21番地'],
  ['0521431', 'にかほ市金浦', 'にかほ市役所金浦庁舎', 'Nikaho City Hall Konoura Office', '秋田県にかほ市金浦字花潟93-1'],
  ['0521432', 'にかほ市象潟町浜ノ田', 'にかほ市役所象潟庁舎', 'Nikaho City Hall Kisakata Office', '秋田県にかほ市象潟町字浜ノ田1番地'],
];
const localCodes = data.stations.filter((s) => s.municipalityCode === '0521400' && s.providerJa === '地方公共団体').map((s) => s.code).sort();
assert.deepEqual(localCodes, updates.map(([code]) => code).sort());
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
    placementLocalityJa: publishedAddressJa.replace(/^秋田県/, ''),
    placementPrecision: 'exact_address',
    metadataStatus: 'Direct official operational statement for three hall meters and published hall addresses',
    note: 'Nikaho mayoral municipal-administration report delivered on 22 February 2024, R60222siseihoukoku.pdf, printed/PDF page 2, explicitly reports that the meters at all three city halls recorded Shindo 3 during the 1 January 2024 Noto Peninsula earthquake. The cover and complete relevant page were text-read, and the operational host statement was visually inspected. The official report index identifies the delivery date. The current municipal hall/department guide, updated 10 April 2026, explicitly identifies the three halls as Kisakata, Konoura and Nikaho and publishes their respective full Japanese addresses; the municipal hall-establishment ordinance independently corroborates the three identities and addresses. Composite matching pairs these directly documented hall instruments with the three local-government reporting station localities: Hirasawa with Nikaho Office, Konoura with Konoura Office, and Kisakatamachi Hamanota with Kisakata Office. No source is claimed to publish the seven-digit JMA station codes or this complete mapping in one table. The host statement, not the city-hall contact footer, catalogue coordinates, proximity, or locality alone, establishes instrument placement. The three community halls opened as evacuation shelters in the following paragraph are not the meter hosts. This is a dated operational statement paired with the current published host addresses, not a fresh 2026 meter inspection. No room, floor, precise within-site position, or installation/transfer date is asserted. The English facility label is descriptive, not claimed as an official English title; approved English and Japanese station names remain unchanged.',
  });
  for (const url of [
    'https://www.city.nikaho.akita.jp/material/files/group/5/R60222siseihoukoku.pdf',
    'https://www.city.nikaho.akita.jp/soshikikarasagasu/somuka/gyomuannai/4/1564.html',
    'https://www.city.nikaho.akita.jp/soshikikarasagasu/somuka/gyomuannai/13/925.html',
    'https://www1.g-reiki.net/nikaho/reiki_honbun/r207RG00000003.html',
  ]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Nikaho hall host-address updates; protected-field and address/precision checks passed.`);
