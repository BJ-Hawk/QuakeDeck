import fs from 'node:fs';
import assert from 'node:assert/strict';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const placementUrl = 'https://www.gikai.metro.tokyo.lg.jp/record/general-affairs/2011-11.html';
const updates = [
  ['1322851', 'あきる野市伊奈', '秋川消防署', 'Akigawa Fire Station', '東京都あきる野市伊奈466番地', 'https://www.tfd.metro.tokyo.lg.jp/fs/akigawa/index.html'],
  ['1320550', '青梅市日向和田', '青梅消防署日向和田出張所', 'Ome Fire Station, Hinatawada Branch', '東京都青梅市日向和田2-309-1', 'https://www.tfd.metro.tokyo.lg.jp/fs/oume/index.html'],
];
for (const [code, nameJa, facilityNameJa, facilityNameEn, publishedAddressJa, addressUrl] of updates) {
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
    metadataStatus: 'Official metropolitan instrument-host statement and current fire-service address',
    note: `Tokyo Metropolitan Assembly General Affairs Committee proceedings No. 11, 4 October 2011, record General Disaster Prevention Department Director Muramatsu's answer that the metropolitan network's 37 Tama instruments include meters installed at Akigawa Fire Station, Hinode Town Hall, Hinohara Village Hall and Ome Fire Station Hinatawada Branch. The placement comes from the responsible official's explicit answer, not the preceding councillor's question, the generic network description, or the separate Tokyo Gas observations discussed afterward. The Tokyo Fire Department's current host guide identifies ${facilityNameJa} and publishes ${publishedAddressJa}; the Tokyo prefectural prefix and half-width digits are normalized as address jurisdiction/formatting where necessary. This pairs the named instrument host with the corresponding current observation locality ${nameJa}; neither source is claimed to supply the seven-digit JMA code. The 2011 placement statement is historical evidence, not a fresh 2026 inspection of the meter. No room, floor or precise within-site position is claimed. The English facility label is descriptive, not asserted as an official English title; approved Japanese and English station names remain unchanged.`,
  });
  const urls = [placementUrl, addressUrl];
  if (code === '1322851') urls.push('https://www.city.akiruno.tokyo.jp/0000000795.html');
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
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Akigawa/Hinatawada instrument-address updates; protected-field and address/precision checks passed.`);
