import fs from 'node:fs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const original = fs.readFileSync(path, 'utf8');
assert.equal(createHash('sha256').update(original).digest('hex').toUpperCase(),
  '7265558EB155A9D0366FA1FB95C174A3DC7D3340AC6FB20FB3582002CF7126E4');
const data = JSON.parse(original), before = structuredClone(data);
const main = data.stations.find((r) => r.code === '0921033');
const branch = data.stations.find((r) => r.code === '0921034');
for (const [row, ja, en] of [[main, '大田原市本町', 'Honcho'], [branch, '大田原市湯津上', 'Yuzukami']]) {
  assert.ok(row);
  assert.equal(row.nameJa, ja);
  assert.equal(row.approvedEnglishName, en);
  assert.equal(row.providerJa, '地方公共団体');
  assert.equal(row.placementPrecision, 'municipality_or_ward');
  assert.ok(!row.publishedAddressJa && !row.facilityNameJa && !row.facilityNameEn);
}

Object.assign(main, {
  publishedAddressJa: '栃木県大田原市本町1丁目4番1号',
  facilityNameJa: '大田原市役所本庁舎',
  facilityNameEn: 'Ohtawara City Hall, Main Building',
  placementLocalityJa: '栃木県大田原市本町1丁目4番1号',
  placementPrecision: 'exact_address',
  metadataStatus: 'City-published field-research discussion identifies main-hall measurement site; independent current facility-address directory',
});
main.note += ' Placement evidence checked on 17 October 2026: Ohtawara City fiscal-2023 first comprehensive education meeting record, meeting held 13 June 2023, PDF pages 1, 3 and 4, identifies lecturer 伴敦志 as a city-history natural-science committee member conducting local terrain/geology/weather research. Complete relevant pages and speaker context were read; complete PDF page 4 was rendered and visually inspected. Discussing his local boring-data research, he explicitly refers to intensity measured at the main hall (本庁でとった震度), separately from Yuzukami and Kurobane branch measurements. This supplies a named measurement-host statement, not a generic policy that every government office hosts a meter. The independent current municipal major-facility directory, published 1 April 2026, explicitly lists 市役所 at 大田原市本町1丁目4番1号, telephone 0287-23-1111; its full government-building table was read. The saved address is the published main-hall host-site address associated with the Honcho observation label, not an enclosure survey, room/floor, source-printed seven-digit JMA crosswalk or fresh 2026 instrument inspection. The 2023 discussion does not date the underlying individual seismic measurements or certify continuous operation/precise instrument retention through reconstruction. It is retained as dated city-published researcher testimony, not mislabelled an equipment inventory or completion certificate. The separate Yuzukami conjecture is not used to infer the main-hall host. No catalogue/provider coordinates, marker proximity, contact-address-only match, office-move assumption or general installation policy was used. The English facility name is descriptive; approved station names, coordinates and prior provenance remain unchanged.';
branch.note += ' Public-source follow-up checked on 17 October 2026: Ohtawara City comprehensive education meeting record of 13 June 2023, complete PDF pages 1, 3 and 4, identifies city-history natural-science committee lecturer 伴敦志 and discusses Yuzukami ground conditions. Complete PDF page 4 was text-read, rendered and visually inspected. The lecturer relates the Yuzukami hall site to intensity measurements but phrases the instrument-installation explanation conjecturally (設置したのかと思います); this is retained as a research lead rather than an unequivocal individual installation inventory. The current city major-facility table publishes 湯津上支所 at 大田原市湯津上5番地1081, but the branch contact/site address alone does not establish the reporting sensor location or historical/current continuity. Facility/address fields and locality precision remain unchanged pending an explicit individual host or sensor retention/transfer source. No placement is inferred from rounded coordinates, locality-name similarity, office proximity or the lecturer\'s conjectural explanation. Approved names, coordinates and prior provenance remain unchanged.';

for (const row of [main, branch]) {
  row.note += ' Other public-source checks on the same date: the FY2019 municipal annual report has 391 pages; all native text was searched for instrument terms. Complete PDF pages 70-71 (printed 60-61) were read and PDF page 71 rendered/visually inspected. Under disaster countermeasures, item 11 records expenditure of 410400 yen for seismometer bollard installation and 756000 yen for a separate fire-radio antenna, but publishes no individual instrument host, work-site address or station-code association. That expenditure is not used to assign either station. The current adopted main disaster plan (chiikibousaiR07.pdf, 218 pages, 188791 extracted characters) and appendix (chiikibousaishiryouR07.pdf, 211 pages, 225591 extracted characters) were fully native-text searched with whitespace-normalized instrument/network terms. The main had no matches; appendix PDF pages 12-13 contain generic JMA intensity-scale explanations, not a city instrument inventory. These are native-text negatives, not complete visual/OCR examinations of every page. The current business-continuity plan, complete PDF page 9 / printed 6, states that the rebuilt main hall entered service in January 2019; this is office context, not a sensor-transfer certificate.';
}
for (const row of [main, branch]) for (const url of [
  'https://www.city.ohtawara.tochigi.jp/docs/2015070300033/file_contents/R0506sougoukyouiku.pdf',
  'https://www.city.ohtawara.tochigi.jp/docs/2013082774873/',
  'https://www.city.ohtawara.tochigi.jp/docs/2019120200014/file_contents/R1shiseinennpou.pdf',
  'https://www.city.ohtawara.tochigi.jp/docs/2019041800034/',
  'https://www.city.ohtawara.tochigi.jp/docs/2019041800034/file_contents/chiikibousaiR07.pdf',
  'https://www.city.ohtawara.tochigi.jp/docs/2019041800034/file_contents/chiikibousaishiryouR07.pdf',
  'https://www.city.ohtawara.tochigi.jp/docs/2019122600010/file_contents/file_2025521310322_1.pdf',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

const allowed = new Set(['publishedAddressJa', 'facilityNameJa', 'facilityNameEn',
  'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'sourceUrls', 'note']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((r) => r.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  const selected = row.code === main.code || row.code === branch.code;
  if (!selected) assert.deepEqual(row, old);
  else {
    assert.deepEqual(Object.keys(row), Object.keys(old));
    const permitted = row.code === main.code ? allowed : new Set(['note', 'sourceUrls']);
    for (const key of Object.keys(old)) if (!permitted.has(key)) assert.deepEqual(row[key], old[key]);
    assert.ok(row.note.startsWith(old.note));
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa),
    `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) {
  fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  assert.deepEqual(JSON.parse(fs.readFileSync(path, 'utf8')), data);
}
console.log(`${apply ? 'Applied' : 'Would apply'} Ohtawara Honcho recorded-measurement host/site address and Yuzukami conjecture qualification; protected fields, unrelated records, order and prior provenance preserved; zero address/precision mismatches. Coverage delta only: exact +1, municipality/ward -1, remaining without facility/address -1; facility-only unchanged.`);
