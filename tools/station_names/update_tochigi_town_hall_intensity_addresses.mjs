import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const updates = [
  {
    code: '0930130',
    nameJa: '上三川町しらさぎ',
    facilityJa: '上三川町役場',
    facilityEn: 'Kaminokawa Town Hall',
    address: '栃木県河内郡上三川町しらさぎ1丁目1番地',
    placementSource: 'https://town.kaminokawa.lg.jp/0005/info-0000004062-0.html',
    addressSource: 'https://town.kaminokawa.lg.jp/0163/info-0000000667-0.html',
    note: 'Kaminokawa Town states that the seismic-intensity meter was stopped for Town Hall renovation and resumed after that work. This is the sole current local-government observation point in the municipality; the town publishes the Town Hall address.'
  },
  {
    code: '0938630',
    nameJa: '高根沢町石末',
    facilityJa: '高根沢町役場',
    facilityEn: 'Takanezawa Town Hall',
    address: '栃木県塩谷郡高根沢町大字石末2053番地',
    placementSource: 'https://www.town.takanezawa.tochigi.jp/gyosei/kocho/pub/documents/siryou.pdf',
    addressSource: 'https://www.town.takanezawa.tochigi.jp/gyosei/chosha/yakuba/yakuba.html',
    note: 'Takanezawa Town’s disaster-plan appendix lists the measuring seismic-intensity meter at Takanezawa Town Hall. This is the sole current local-government observation point in the municipality; the town publishes the Town Hall address.'
  }
];

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
for (const update of updates) {
  const station = parsed.stations.find((candidate) => candidate.code === update.code);
  if (!station || station.nameJa !== update.nameJa || station.prefectureJa !== '栃木県' ||
      station.municipalityStationCount !== 1 || station.placementPrecision !== 'municipality_or_ward' ||
      station.publishedAddressJa !== null || station.facilityNameJa !== null) {
    throw new Error(`Unexpected starting state for ${update.code}`);
  }
}
console.log(`Tochigi municipal address update: ${updates.map(({ code }) => code).join(', ')}`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded updates.');
  process.exit(0);
}

let revisedRaw = raw;
for (const update of [...updates].reverse()) {
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
  let revised = record
    .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(update.address)},`)
    .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(update.facilityJa)},`)
    .replace('      "facilityNameEn": null,', `      "facilityNameEn": ${JSON.stringify(update.facilityEn)},`)
    .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official municipal seismic-meter placement",')
    .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(update.note)},\n`)
    .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
  for (const source of [update.placementSource, update.addressSource]) {
    if (!revised.includes(source)) {
      revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(source)},`);
    }
  }
  revisedRaw = Buffer.concat([revisedRaw.subarray(0, start), Buffer.from(revised, 'utf8'), revisedRaw.subarray(end)]);
}
fs.writeFileSync(inputPath, revisedRaw);

const updated = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
for (const update of updates) {
  const station = updated.stations.find((candidate) => candidate.code === update.code);
  if (!station || station.publishedAddressJa !== update.address || station.facilityNameJa !== update.facilityJa ||
      station.facilityNameEn !== update.facilityEn || station.placementPrecision !== 'exact_address' ||
      !station.sourceUrls.includes(update.placementSource) || !station.sourceUrls.includes(update.addressSource)) {
    throw new Error(`Post-write validation failed for ${update.code}`);
  }
}
console.log(`Applied Tochigi municipal address update: ${updates.map(({ code }) => code).join(', ')}`);
