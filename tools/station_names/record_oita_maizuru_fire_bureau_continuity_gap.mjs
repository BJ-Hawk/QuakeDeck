import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '4420134';
const row = data.stations.find((r) => r.code === code);
assert.ok(row);
assert.equal(row.nameJa, '大分市舞鶴町');
assert.equal(row.approvedEnglishName, 'Maizurumachi');
assert.equal(row.placementPrecision, 'municipality_or_ward');
assert.ok(!row.facilityNameJa && !row.facilityNameEn && !row.publishedAddressJa);
const marker = 'Oita Fire Bureau continuity qualification checked on 17 October 2026:';
assert.ok(!row.note.includes(marker), 'Already recorded');
row.note += ` ${marker} an indexed March 2023 city earthquake-plan passage states that an instrumental intensity meter was installed at the Fire Bureau, but the old sinnsai.pdf URL now returns HTTP 404 and the complete original page was not recovered or inspected. It is a qualified historical lead, not a current host certification. The official current disaster-plan index, updated 8 July 2026, links the March 2025 earthquake plan at shinsaitaisakuhen.pdf. Its complete PDF page 122 (bottom printed folio 108, with a separate inner text number 103) was text-read using PDFium, rendered and visually inspected. The corresponding warning-reception paragraph now ends with fax reception and does not contain the old meter-installation sentence. Removal of that sentence is not evidence that the meter itself was removed or relocated. The official Fire Organization guide, updated 5 November 2024 and explicitly current as of 1 July 2024, separately lists Oita City Fire Bureau at 大分市荷揚町3番45号 and Oita City Central Fire Station at 大分市舞鶴町一丁目1番1号. The complete facility table and its heading were read; the independent Central Fire Station contact guide also publishes the Maizurumachi address. Neither the relocated Bureau office address nor the retained Central Fire Station address is assigned to this reporting instrument without direct meter-retention/transfer or explicit current host evidence. No facility/address field is promoted, and no instrument-removal date, sensor-transfer completion, floor, room or exact enclosure is inferred. Association is not inferred from coordinates, proximity or a shared locality. Approved names, coordinates and prior provenance remain unchanged.`;
for (const url of [
  'https://www.city.oita.oita.jp/o009/kurashi/anshinanzen/documents/sinnsai.pdf',
  'https://www.city.oita.oita.jp/o009/kurashi/anshinanzen/1272444132022.html',
  'https://www.city.oita.oita.jp/o009/kurashi/anshinanzen/documents/shinsaitaisakuhen.pdf',
  'https://www.city.oita.oita.jp/o212/kurashi/anshinanzen/1180577976689.html',
  'https://www.city.oita.oita.jp/o029/shisejoho/annai/1000000000193.html',
]) if (!row.sourceUrls.includes(url)) row.sourceUrls.push(url);

assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((r) => r.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const r = data.stations[i], old = before.stations[i];
  assert.equal(r.code, old.code);
  if (r.code !== code) assert.deepEqual(r, old);
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
console.log(`${apply ? 'Applied' : 'Would apply'} Oita Maizurumachi Fire Bureau continuity qualification; all placement fields, protected fields, unrelated rows, order and previous provenance preserved; zero address/precision mismatches. No coverage-count change.`);
