import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1034530';
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '1034500').map((row) => row.code), [code]);
const s = data.stations.find((row) => row.code === code);
assert.ok(s);
assert.equal(s.nameJa, '吉岡町下野田');
assert.equal(s.providerJa, '地方公共団体');
assert.equal(s.placementPrecision, 'municipality_or_ward');
assert.ok(!s.facilityNameJa && !s.facilityNameEn && !s.publishedAddressJa);
Object.assign(s, {
  facilityNameJa: '吉岡町役場',
  facilityNameEn: 'Yoshioka Town Hall',
  publishedAddressJa: '群馬県北群馬郡吉岡町大字下野田560番地',
  placementLocalityJa: '北群馬郡吉岡町大字下野田560番地',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct adopted municipal disaster-plan instrument-installation agreement and official facility address',
  note: 'Yoshioka adopted February 2026 disaster plan, confirmed by its official parent index updated 4 March 2026, appendix volume 1, 4b62ad06edd8b5f72cc827b16b55108c.pdf, printed pages 221-222 / PDF pages 17-18, reproduces the Gunma Prefecture/Yoshioka intensity-information-network installation agreement. Both complete scanned agreement pages were directly downloaded in memory and visually inspected; searchable headings alone were not used. Article 2 explicitly places the network equipment at the town government building, article 3 provides use of its building facilities and grounds, and article 7 specifically refers to the intensity meter. The agreement is dated 1 January 1997 and includes automatic renewal; its inclusion in the adopted 2026 plan is not represented as a fresh 2026 sensor inspection. The referenced separate equipment schedule is not reproduced between this agreement and the next agreement, so no itemized schedule, specific wing, room, floor or precise within-site instrument location is claimed. The official Town Hall access page publishes 群馬県北群馬郡吉岡町大字下野田560番地; an independent official municipal article updated 19 August 2026 explicitly identifies the Town Hall building at 吉岡町大字下野田560番地 in its body text, rather than relying only on a department contact address. Composite matching uses the explicit municipal instrument-host agreement, published Shimonoda hall address and sole bundled Yoshioka reporting station; no source is claimed to publish the seven-digit station code. No installation or physical relocation date is inferred. English facility label is descriptive; approved station names remain unchanged.',
});
for (const url of [
  'https://www.town.yoshioka.lg.jp/chousei/keikaku/kankyo/chiiki_bosai.html',
  'https://www.town.yoshioka.lg.jp/chousei/pdf/4b62ad06edd8b5f72cc827b16b55108c.pdf',
  'https://www.town.yoshioka.lg.jp/chousei/access/',
  'https://www.town.yoshioka.lg.jp/fukushi/syogai/post_83.html',
]) if (!s.sourceUrls.includes(url)) s.sourceUrls.push(url);

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
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Yoshioka Town Hall address; protected-field and address/precision checks passed.`);
