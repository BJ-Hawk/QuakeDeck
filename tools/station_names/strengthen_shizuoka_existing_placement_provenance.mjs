import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const source = 'https://www.pref.shizuoka.jp/_res/projects/default_project/_page_/001/029/862/2-2-5.pdf';
const priorStatus = '      "metadataStatus": "Catalogue only",';
const priorNote = '      "note": "No exact address or precise provider-station metadata is recorded yet.",';
const nextStatus = '      "metadataStatus": "Official prefectural seismic-meter placement and address",';
const nextNote = `      "note": ${JSON.stringify('Shizuoka Prefecture’s official disaster-plan station table directly identifies the meter host facility and publishes its Japanese address.')},`;

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const codes = parsed.stations
  .filter((station) => station.placementPrecision === 'exact_address' && station.metadataStatus === 'Catalogue only' && station.sourceUrls.includes(source))
  .map((station) => station.code);
if (codes.length !== 33) throw new Error(`Expected 33 matching Shizuoka records, found ${codes.length}`);
console.log(`Shizuoka placement provenance update: ${codes.length} records`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded updates.');
  process.exit(0);
}

let output = raw;
for (const code of codes) {
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
console.log(`Applied Shizuoka placement provenance update: ${codes.length} records`);
