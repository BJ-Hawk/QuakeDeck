import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '3250134';
const address = '島根県鹿足郡津和野町枕瀬218-18';
const facility = '津和野町役場';
const source = 'https://www.city.unnan.shimane.jp/unnan/kurashi/bousai/bousaijouhou/files/R6_shinsaitaisakuhen.pdf';

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === code);
if (!station || station.nameJa !== '津和野町枕瀬' || station.prefectureJa !== '島根県' ||
    station.placementPrecision !== 'municipality_or_ward' || station.publishedAddressJa !== null ||
    station.facilityNameJa !== null) {
  throw new Error(`Unexpected starting state for ${code}`);
}
console.log(`Tsuwano Town Hall address update: ${code} (${station.nameJa})`);
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

const note = 'An official regional disaster-plan observation-site table identifies the 島根県 station 津和野町枕瀬 at Tsuwano Town Hall and publishes its Japanese address.';
let revised = record
  .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(address)},`)
  .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(facility)},`)
  .replace('      "facilityNameEn": null,', '      "facilityNameEn": "Tsuwano Town Hall",')
  .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official regional disaster-plan observation-site table and published facility address",')
  .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(note)},\n`)
  .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
if (!revised.includes(source)) {
  revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(source)},`);
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));

const updated = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
const updatedStation = updated.stations.find((candidate) => candidate.code === code);
if (!updatedStation || updatedStation.publishedAddressJa !== address || updatedStation.facilityNameJa !== facility ||
    updatedStation.facilityNameEn !== 'Tsuwano Town Hall' || updatedStation.placementPrecision !== 'exact_address' ||
    !updatedStation.sourceUrls.includes(source)) {
  throw new Error(`Post-write validation failed for ${code}`);
}
console.log(`Applied Tsuwano Town Hall address update: ${code}`);
