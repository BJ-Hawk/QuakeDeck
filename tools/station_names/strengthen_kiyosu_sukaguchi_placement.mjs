import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '2323334';
const placementSource = 'https://www.pref.aichi.jp/uploaded/attachment/488300.pdf';

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === code);
if (!station) throw new Error(`Missing station ${code}`);
if (station.placementPrecision !== 'exact_address' || station.publishedAddressJa !== '愛知県清須市須ヶ口1238番地' || station.facilityNameJa !== '清須市役所南館') {
  throw new Error(`Unexpected starting state for ${code}`);
}

console.log(`Kiyosu placement provenance update: ${code} (${station.nameJa})`);
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
  '      "metadataStatus": "Catalogue only",',
  '      "note": "No exact address or precise provider-station metadata is recorded yet.",',
  '      "placementPrecision": "exact_address"',
]) {
  if (!record.includes(expected)) throw new Error(`Unexpected record content for ${code}`);
}

const note = 'Aichi Prefecture’s official meteorological-observation equipment table directly identifies the Kiyosu meter at Kiyosu City Hall South Building and publishes its Japanese address.';
let revised = record
  .replace('      "metadataStatus": "Catalogue only",', '      "metadataStatus": "Official prefectural seismic-meter placement and address",')
  .replace('      "note": "No exact address or precise provider-station metadata is recorded yet.",', `      "note": ${JSON.stringify(note)},`);
if (!revised.includes(placementSource)) {
  revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(placementSource)},`);
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));
console.log(`Applied Kiyosu placement provenance update: ${code}`);
