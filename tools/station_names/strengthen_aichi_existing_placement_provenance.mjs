import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const updates = [
  ['2310632', '愛知県庁西庁舎', '愛知県名古屋市中区三の丸二丁目4番1号'],
  ['2320336', '一宮市消防本部', '愛知県一宮市緑一丁目1番10号'],
  ['2323534', '弥富市役所図書館棟', '愛知県弥富市前ヶ須南本田347番地'],
];
const source = 'https://www.pref.aichi.jp/uploaded/attachment/542306.pdf';
const priorStatus = '      "metadataStatus": "Catalogue only",';
const priorNote = '      "note": "No exact address or precise provider-station metadata is recorded yet.",';
const nextStatus = '      "metadataStatus": "Official prefectural seismic-meter placement and address",';
const nextNote = `      "note": ${JSON.stringify('Aichi Prefecture’s official meteorological-observation equipment table directly identifies the meter host facility and publishes its Japanese address.')},`;

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
for (const [code, facility, address] of updates) {
  const station = parsed.stations.find((candidate) => candidate.code === code);
  if (!station || station.placementPrecision !== 'exact_address' || station.facilityNameJa !== facility || station.publishedAddressJa !== address || !station.sourceUrls.includes(source)) {
    throw new Error(`Unexpected starting state for ${code}`);
  }
}
console.log(`Aichi existing-placement provenance update: ${updates.map(([code]) => code).join(', ')}`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded updates.');
  process.exit(0);
}

let output = raw;
for (const [code] of updates) {
  const startMarker = Buffer.from(`    {\n      "code": "${code}",`, 'utf8');
  const start = output.indexOf(startMarker);
  if (start < 0) throw new Error(`Cannot find record ${code}`);
  const end = output.indexOf(Buffer.from('\n    },', 'utf8'), start);
  if (end < 0) throw new Error(`Cannot find end of record ${code}`);
  const record = output.subarray(start, end).toString('utf8');
  if (record.includes('\uFFFD') || !record.includes(priorStatus) || !record.includes(priorNote)) throw new Error(`Unexpected record content for ${code}`);
  const revised = record.replace(priorStatus, nextStatus).replace(priorNote, nextNote);
  output = Buffer.concat([output.subarray(0, start), Buffer.from(revised, 'utf8'), output.subarray(end)]);
}
fs.writeFileSync(inputPath, output);
console.log(`Applied Aichi existing-placement provenance update: ${updates.length} records`);
