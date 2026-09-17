import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '4021035';
const address = '福岡県八女市星野村13102番地1';
const facility = '八女市星野支所（旧星野村役場）';
const placementSource = 'https://www.joho.tagawa.fukuoka.jp/bousai/kiji0031997/3_1997_7322_up_324qazyd.pdf';
const addressSource = 'https://www.city.yame.fukuoka.jp/soshiki/3/17/1/5/branch/1455882117410.html';

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === code);
if (!station || station.placementPrecision !== 'municipality_or_ward' || station.publishedAddressJa !== null || station.facilityNameJa !== null) {
  throw new Error(`Unexpected starting state for ${code}`);
}
console.log(`Yame Hoshino Branch address update: ${code} (${station.nameJa})`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded update.');
  process.exit(0);
}

const startMarker = Buffer.from(`    {\n      "code": "${code}",`, 'utf8');
const start = raw.indexOf(startMarker);
if (start < 0) throw new Error(`Cannot find record ${code}`);
const end = raw.indexOf(Buffer.from('\n    },', 'utf8'), start);
if (end < 0) throw new Error(`Cannot find end of record ${code}`);
const record = raw.subarray(start, end).toString('utf8');
if (record.includes('\uFFFD')) throw new Error(`Refusing to rewrite invalid UTF-8 in ${code}`);
for (const expected of [
  '      "publishedAddressJa": null,',
  '      "facilityNameJa": null,',
  '      "placementPrecision": "municipality_or_ward"',
]) {
  if (!record.includes(expected)) throw new Error(`Unexpected record content for ${code}`);
}
const note = 'Tagawa City’s official disaster-plan appendix lists the Fukuoka prefectural meter at Hoshino Village Office. Yame City identifies the successor Hoshino Branch at the same published address.';
let revised = record
  .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(address)},`)
  .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(facility)},`)
  .replace('      "facilityNameEn": null,', '      "facilityNameEn": "Yame City Hoshino Branch (former Hoshino Village Office)",')
  .replace('      "metadataStatus": "Catalogue only",', '      "metadataStatus": "Official prefectural seismic-meter placement and successor facility address",')
  .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(note)},\n`)
  .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
for (const source of [placementSource, addressSource]) {
  if (!revised.includes(source)) {
    revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(source)},`);
  }
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));
JSON.parse(fs.readFileSync(inputPath, 'utf8'));
console.log(`Applied Yame Hoshino Branch address update: ${code}`);
