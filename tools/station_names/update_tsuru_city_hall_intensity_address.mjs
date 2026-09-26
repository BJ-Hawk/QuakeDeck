import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const update = {
  code: '1920431',
  nameJa: '都留市上谷',
  facilityJa: '都留市役所',
  facilityEn: 'Tsuru City Hall',
  address: '山梨県都留市上谷一丁目1番1号',
  placementSource: 'https://www.city.tsuru.yamanashi.jp/material/files/group/2/04.pdf',
  addressSource: 'https://www.city.tsuru.yamanashi.jp/soshiki/somu/houseikouhou_t/1/1776.html',
  note: 'Tsuru City’s disaster plan states that its metered seismic-intensity instrument is installed at City Hall. The city publishes City Hall at Uetani 1-chome 1-1, matching the sole current local-government observation-point locality in the municipality.'
};

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === update.code);
if (!station || station.nameJa !== update.nameJa || station.prefectureJa !== '山梨県' ||
    station.municipalityStationCount !== 1 || station.placementPrecision !== 'municipality_or_ward' ||
    station.publishedAddressJa !== null || station.facilityNameJa !== null) {
  throw new Error(`Unexpected starting state for ${update.code}`);
}
console.log(`Tsuru City Hall intensity-address update: ${update.code}`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded update.');
  process.exit(0);
}

const startMarker = Buffer.from(`    {\n      "code": "${update.code}",`, 'utf8');
const start = raw.indexOf(startMarker);
if (start < 0 || raw.indexOf(startMarker, start + 1) >= 0) throw new Error(`Cannot uniquely find record ${update.code}`);
const end = raw.indexOf(Buffer.from('\n    },', 'utf8'), start);
if (end < 0) throw new Error(`Cannot find end of record ${update.code}`);
const record = raw.subarray(start, end).toString('utf8');
for (const expected of ['      "publishedAddressJa": null,', '      "facilityNameJa": null,', '      "placementPrecision": "municipality_or_ward"']) {
  if (!record.includes(expected)) throw new Error(`Unexpected record content for ${update.code}`);
}
let revised = record
  .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(update.address)},`)
  .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(update.facilityJa)},`)
  .replace('      "facilityNameEn": null,', `      "facilityNameEn": ${JSON.stringify(update.facilityEn)},`)
  .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official municipal seismic-meter placement",')
  .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(update.note)},\n`)
  .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
for (const source of [update.placementSource, update.addressSource]) {
  if (!revised.includes(source)) revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(source)},`);
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));

const updated = JSON.parse(fs.readFileSync(inputPath, 'utf8')).stations.find((candidate) => candidate.code === update.code);
if (!updated || updated.publishedAddressJa !== update.address || updated.facilityNameJa !== update.facilityJa || updated.facilityNameEn !== update.facilityEn || updated.placementPrecision !== 'exact_address' || !updated.sourceUrls.includes(update.placementSource) || !updated.sourceUrls.includes(update.addressSource)) throw new Error(`Post-write validation failed for ${update.code}`);
console.log(`Applied Tsuru City Hall intensity-address update: ${update.code}`);
