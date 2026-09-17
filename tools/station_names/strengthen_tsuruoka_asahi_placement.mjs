import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '0620335';
const placementSource = 'https://www.city.tsuruoka.lg.jp/sangyo/nyusatsu/tsuruokaguideline.files/R7.5.pdf';

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === code);
if (!station) throw new Error(`Missing station ${code}`);
if (station.placementPrecision !== 'exact_address' || station.publishedAddressJa !== '山形県鶴岡市下名川字落合1' || station.facilityNameJa !== '鶴岡市朝日庁舎') {
  throw new Error(`Unexpected starting state for ${code}`);
}

console.log(`Tsuruoka placement provenance update: ${code} (${station.nameJa})`);
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

const note = 'The FDMA-hosted Yamagata disaster-plan annex publishes the station address at Tsuruoka City Asahi Branch Office. Tsuruoka City’s 2025 official contract further confirms that the prefectural intensity meter is installed on that office’s grounds and was relocated there during reconstruction.';
let revised = record
  .replace('      "metadataStatus": "Catalogue only",', '      "metadataStatus": "Official municipal seismic-meter placement and address",')
  .replace('      "note": "No exact address or precise provider-station metadata is recorded yet.",', `      "note": ${JSON.stringify(note)},`);
if (!revised.includes(placementSource)) {
  revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(placementSource)},`);
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));
console.log(`Applied Tsuruoka placement provenance update: ${code}`);
