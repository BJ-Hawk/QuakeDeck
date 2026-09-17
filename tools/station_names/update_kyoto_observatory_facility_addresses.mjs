import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const observatorySource = 'https://www.bousai.city.kyoto.lg.jp/cmsfiles/contents/0000000/148/siryou1.pdf';

const updates = [
  {
    code: '2610230',
    nameJa: '京都上京区薮ノ内町',
    facilityNameJa: '京都府庁',
    facilityNameEn: 'Kyoto Prefectural Government Office',
    address: '京都府京都市上京区下立売通新町西入薮ノ内町',
    addressSource: 'https://www.pref.kyoto.jp/access.html',
  },
  {
    code: '2610340',
    nameJa: '京都左京区田中',
    facilityNameJa: '左京消防署',
    facilityNameEn: 'Sakyo Fire Station',
    address: '京都府京都市左京区田中西大久保町36番地',
    addressSource: 'https://www.city.kyoto.lg.jp/gyozai/cmsfiles/contents/0000287/287682/R07_shisetsu.pdf',
  },
];

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
for (const update of updates) {
  const station = parsed.stations.find((candidate) => candidate.code === update.code);
  if (!station || station.nameJa !== update.nameJa || station.placementPrecision !== 'municipality_or_ward' ||
      station.publishedAddressJa !== null || station.facilityNameJa !== null) {
    throw new Error(`Unexpected starting state for ${update.code}`);
  }
}
console.log(`Kyoto observatory facility-address updates: ${updates.map((update) => update.code).join(', ')}`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded updates.');
  process.exit(0);
}

let revisedRaw = raw;
for (const update of updates) {
  const startMarker = Buffer.from(`    {\n      "code": "${update.code}",`, 'utf8');
  const start = revisedRaw.indexOf(startMarker);
  if (start < 0 || revisedRaw.indexOf(startMarker, start + 1) >= 0) throw new Error(`Cannot uniquely find record ${update.code}`);
  const end = revisedRaw.indexOf(Buffer.from('\n    },', 'utf8'), start);
  if (end < 0) throw new Error(`Cannot find end of record ${update.code}`);
  const record = revisedRaw.subarray(start, end).toString('utf8');
  if (record.includes('\uFFFD')) throw new Error(`Refusing to rewrite invalid UTF-8 in ${update.code}`);
  for (const expected of [
    '      "publishedAddressJa": null,',
    '      "facilityNameJa": null,',
    '      "placementPrecision": "municipality_or_ward"',
  ]) {
    if (!record.includes(expected)) throw new Error(`Unexpected record content for ${update.code}`);
  }
  const note = 'Kyoto City’s disaster-plan appendix identifies this locality’s seismic observatory at the named facility; the relevant authority publishes the facility address.';
  let revised = record
    .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(update.address)},`)
    .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(update.facilityNameJa)},`)
    .replace('      "facilityNameEn": null,', `      "facilityNameEn": ${JSON.stringify(update.facilityNameEn)},`)
    .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official municipal disaster-plan observatory placement and published facility address",')
    .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(note)},\n`)
    .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
  for (const source of [observatorySource, update.addressSource]) {
    if (!revised.includes(source)) revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(source)},`);
  }
  revisedRaw = Buffer.concat([revisedRaw.subarray(0, start), Buffer.from(revised, 'utf8'), revisedRaw.subarray(end)]);
}
fs.writeFileSync(inputPath, revisedRaw);

const updated = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
for (const update of updates) {
  const station = updated.stations.find((candidate) => candidate.code === update.code);
  if (!station || station.facilityNameJa !== update.facilityNameJa || station.facilityNameEn !== update.facilityNameEn ||
      station.publishedAddressJa !== update.address || station.placementPrecision !== 'exact_address' ||
      !station.sourceUrls.includes(observatorySource) || !station.sourceUrls.includes(update.addressSource)) {
    throw new Error(`Post-write validation failed for ${update.code}`);
  }
}
console.log(`Applied Kyoto observatory facility-address updates: ${updates.map((update) => update.code).join(', ')}`);
