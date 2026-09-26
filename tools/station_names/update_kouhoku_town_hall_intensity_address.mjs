import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '4142431';
const address = '佐賀県杵島郡江北町大字山口1651番地1';
const facility = '江北町役場';
const placementSource = 'https://www.town.kouhoku.saga.jp/kiji008573/index.html';
const addressSource = 'https://www.town.kouhoku.saga.jp/kiji0032029/index.html';

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === code);
if (!station || station.nameJa !== '江北町山口' || station.prefectureJa !== '佐賀県' ||
    station.municipalityStationCount !== 1 || station.placementPrecision !== 'municipality_or_ward' ||
    station.publishedAddressJa !== null || station.facilityNameJa !== null) {
  throw new Error(`Unexpected starting state for ${code}`);
}
console.log(`Kouhoku Town Hall address update: ${code} (${station.nameJa})`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded update.');
  process.exit(0);
}

const startMarker = Buffer.from(`    {\n      "code": "${code}",`, 'utf8');
const start = raw.indexOf(startMarker);
if (start < 0 || raw.indexOf(startMarker, start + 1) >= 0) throw new Error(`Cannot uniquely find record ${code}`);
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

const note = 'Kouhoku Town’s emergency notice states that it confirmed an earthquake using the seismometer at Town Hall. This is the sole current local-government observation point in the municipality, whose published locality is 江北町山口; the town publishes the Town Hall address.';
let revised = record
  .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(address)},`)
  .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(facility)},`)
  .replace('      "facilityNameEn": null,', '      "facilityNameEn": "Kouhoku Town Hall",')
  .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official municipal seismic-meter placement",')
  .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(note)},\n`)
  .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
for (const source of [placementSource, addressSource]) {
  if (!revised.includes(source)) {
    revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(source)},`);
  }
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));

const updated = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
const updatedStation = updated.stations.find((candidate) => candidate.code === code);
if (!updatedStation || updatedStation.publishedAddressJa !== address || updatedStation.facilityNameJa !== facility ||
    updatedStation.facilityNameEn !== 'Kouhoku Town Hall' || updatedStation.placementPrecision !== 'exact_address' ||
    !updatedStation.sourceUrls.includes(placementSource) || !updatedStation.sourceUrls.includes(addressSource)) {
  throw new Error(`Post-write validation failed for ${code}`);
}
console.log(`Applied Kouhoku Town Hall address update: ${code}`);
