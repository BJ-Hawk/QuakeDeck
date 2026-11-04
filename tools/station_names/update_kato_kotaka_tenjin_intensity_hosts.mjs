import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const hostSource = 'https://www.city.kato.lg.jp/kakukanogoannai/soumuzaiseibu/bosaika/bosai001/14915.html';
const cityDirectory = 'https://www.city.kato.lg.jp/kakukanogoannai/hisyo/hisho/kohokochokakari/homepage/1454057474610.html';
assert.deepEqual(data.stations.filter((row) => row.municipalityCode === '2822800').map((row) => row.code).sort(), ['2822801', '2822832', '2822833']);
const updates = [
  {
    code: '2822832', nameJa: '加東市河高',
    facilityNameJa: '加東市滝野総合公園体育館（スカイピア）',
    facilityNameEn: 'Takino General Park Gymnasium (Skypia)',
    publishedAddressJa: '兵庫県加東市河高4007番地',
    placementLocalityJa: '加東市河高4007番地',
    placementPrecision: 'exact_address',
    metadataStatus: 'Direct municipal instrument-host statement and independently published official facility address',
    sources: [hostSource, 'https://www.city.kato.lg.jp/kakukanogoannai/kyouikushinkoubu/shogaigakushuka/supotu/1454058387212.html', cityDirectory],
    note: 'Kato City official disaster-prevention article 市内への影響が見込まれる断層, updated 29 March 2024, explicitly states that the city has intensity meters at three named sites, including 加東市滝野総合公園体育館. The complete article body was read; the nearby predicted-intensity maps were not used as instrument-position evidence. The current official sports-facility guide independently identifies the gymnasium as スカイピア and publishes 加東市河高4007番地 in its indoor-facility table and named gymnasium section; the municipal major-public-facilities directory independently confirms the same address. Composite matching uses the explicit three-host statement, independently published Kotaka gymnasium address and the three bundled Kato station identities, not catalogue coordinates or proximity. The separately named Takino Sports Center at Kamitakino 1167-5 and Takino municipal office/dispatch center at Shimotakino 1269-2 are not this host. No source is claimed to publish the seven-digit station code, precise within-site sensor position, current room/floor, installation/transfer date or fresh 2026 sensor inspection. English facility label is descriptive; approved station names remain unchanged.',
  },
  {
    code: '2822833', nameJa: '加東市天神',
    facilityNameJa: '北はりま消防組合加東消防署東条出張所',
    facilityNameEn: 'Kato Fire Station Tojo Branch',
    publishedAddressJa: null,
    placementLocalityJa: '加東市天神',
    placementPrecision: 'municipality_or_ward',
    metadataStatus: 'Direct municipal instrument-host identification; conflicting official facility addresses withheld',
    sources: [hostSource, 'https://kitaharima119.net/home/sisetu/sisetu.html', 'https://kitaharima119.net/home/gyousei/nyuusatu/230615-1.html', cityDirectory],
    note: 'Kato City official disaster-prevention article 市内への影響が見込まれる断層, updated 29 March 2024, explicitly identifies 北はりま消防組合加東消防署東条出張所 as one of the city\'s three intensity-meter hosts. The full article body was read. The fire authority\'s current facility guide independently names this branch at 加東市天神123, while its 15 June 2023 procurement notice 230615-1 explicitly identifies the branch work site as 兵庫県加東市天神123番地. However, Kato\'s official major-public-facilities directory, dated 28 December 2021 and still published, identifies the same named branch and telephone number at 加東市天神129番地. All relevant body/table entries were retrieved and read. The branch identity and Tenjin locality are supported, but the competing official parcel numbers are retained here as unresolved evidence; publishedAddressJa is deliberately null and exact_address is not assigned. The 2023 procurement concerns radio base-station relocation supervision, not intensity-meter relocation, and is used only as a published facility-address source. No claim is made that 123 or 129 is a verified current sensor parcel. Matching uses the explicit municipal host statement and branch/locality identity, not coordinates or proximity. No seven-digit-code publication, room/floor, precise within-site sensor location, installation/transfer date or fresh 2026 sensor inspection is asserted. English facility label is descriptive; approved station names remain unchanged.',
  },
];
for (const update of updates) {
  const row = data.stations.find((item) => item.code === update.code);
  assert.ok(row);
  assert.equal(row.nameJa, update.nameJa);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
  for (const key of ['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note']) row[key] = update[key];
  for (const url of update.sources) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
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
console.log(`${apply ? 'Applied' : 'Would apply'} Kato Kotaka exact address and Tenjin facility-only identification; protected-field and address/precision checks passed.`);
