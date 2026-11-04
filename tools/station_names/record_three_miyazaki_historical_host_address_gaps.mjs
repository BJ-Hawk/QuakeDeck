import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  '725DB72602EEF01C7841224A8D2092F09B3E7B0039E7289B839EF8314D1E0832',
  'Source changed since the researched checkpoint; inspect before applying');
const data = JSON.parse(original);
const before = structuredClone(data);
const catalogue = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const common = ' The original March 2001 EDM Technical Report No.12 catalogue, complete PDF page 17 / printed page 12, Table 2-1, was visually read with both network headings and the full relevant row; its cover date was also inspected. This is historical station-to-host evidence, not a current installation certification. No physical visit, later meter-retention/transfer, seven-digit JMA-code crosswalk or exact enclosure is asserted, and no host is inferred from coordinates, proximity or a shared locality. Placement fields stay blank pending current host/continuity evidence; approved names, coordinates and prior provenance are preserved.';
const updates = [
  {
    code: '4540132', name: '高鍋町上江', english: 'Uwae',
    marker: 'Takanabe historical host and address-change qualification checked on 17 October 2026:',
    note: ' the historical intensity-network row 95004 高鍋町上江 explicitly gives 高鍋町大字上江1207-1, 高鍋町役場敷地内. The current municipal Town Hall management rules, Article 2(2), independently define the office at 高鍋町大字上江8437番地. Miyazaki Prefectural Gazette No.2072 of 6 April 2009, complete PDF/printed page 4, Notice 316, was text-read, rendered and visually inspected: its old/new columns record the revenue-stamp sales location inside Town Hall changing from 上江1207-1 to 上江8437, with change date 28 January 2006. That notice concerns stamp sales, not the reporting meter; it does not explicitly establish cadastral renumbering, a physical Town Hall move or a sensor transfer. Thus the different printed addresses alone are not treated as proof that the host parcel physically changed, and neither is promoted to a current meter address without clarification.',
    urls: [catalogue, 'https://www.pref.miyazaki.lg.jp/documents/2404/000118848.pdf', 'https://www.town.takanabe.lg.jp/section/reiki_int/reiki_honbun/q627RG00000030.html'],
  },
  {
    code: '4520238', name: '都城市山之口町花木', english: 'Yamanokuchicho Hananoki',
    marker: 'Yamanokuchi historical host/current office distinction checked on 17 October 2026:',
    note: ' the historical intensity-network row 95037 山之口町花木 explicitly gives 山之口町大字花木2005, 山之口町役場敷地内. The current official Regional Promotion Department / Yamanokuchi General Branch organization page was read in full and gives 山之口町花木1934番地1 for both branch departments. The older facility guide at map/14533.html still publishes 花木2005; that older office-address listing does not override the different current organization contact address or certify a current meter site. Neither 2005 nor 1934-1 is assigned to the reporting instrument without evidence identifying where it remained or was transferred. An office contact-address change is not itself a completed instrument move.',
    urls: [catalogue, 'https://www.city.miyakonojo.miyazaki.jp/map/14533.html', 'https://www.city.miyakonojo.miyazaki.jp/soshiki/9.html'],
  },
  {
    code: '4520239', name: '都城市山田町山田', english: 'Yamadacho Yamada',
    marker: 'Yamada historical host/current office distinction checked on 17 October 2026:',
    note: ' the historical intensity-network row 95039 宮崎山田町山田 explicitly gives 山田町大字山田3881, 山田町役場敷地内. The current official Regional Promotion Department / Yamada General Branch organization page was read in full and gives 山田町山田3881番地7 for both branch departments. The common parent parcel number does not prove that the reporting instrument moved with the office or occupies the present office subparcel. An indexed official facility-record lead at uploaded/attachment/36835.pdf describes branch-office functions moving into Yamada General Center on 30 March 2020, but direct retrieval returned 404 and its complete original page was not recovered or visually inspected; that indexed passage is retained only as a qualified follow-up lead, not meter-transfer evidence. Neither office parcel is promoted to a current instrument address.',
    urls: [catalogue, 'https://www.city.miyakonojo.miyazaki.jp/soshiki/8.html', 'https://www.city.miyakonojo.miyazaki.jp/uploaded/attachment/36835.pdf'],
  },
];
const affected = new Set(updates.map((u) => u.code));
for (const u of updates) {
  const row = data.stations.find((r) => r.code === u.code);
  assert.ok(row);
  assert.equal(row.nameJa, u.name);
  assert.equal(row.approvedEnglishName, u.english);
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
  assert.ok(!row.note.includes(u.marker), 'Already recorded');
  row.note += ` ${u.marker}${u.note}${common}`;
  for (const url of u.urls) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);
}

assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((r) => r.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const r = data.stations[i], old = before.stations[i];
  assert.equal(r.code, old.code);
  if (!affected.has(r.code)) assert.deepEqual(r, old);
  else {
    assert.deepEqual(Object.keys(r), Object.keys(old));
    for (const key of Object.keys(old)) if (!['note', 'sourceUrls'].includes(key)) assert.deepEqual(r[key], old[key]);
    assert.ok(r.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(r.sourceUrls.includes(url));
  }
  assert.equal(r.placementPrecision === 'exact_address', Boolean(r.publishedAddressJa), `Address/precision mismatch ${r.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} three Miyazaki historical-host/current-address qualifications; all placement fields, protected fields, unrelated rows, order and prior provenance preserved; zero address/precision mismatches. No coverage-count change.`);
