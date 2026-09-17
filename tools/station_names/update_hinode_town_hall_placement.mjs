import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '1330540';
const evidenceSource = 'https://www.town.hinode.tokyo.jp/cmsfiles/contents/0000003/3303/keso.pdf';

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === code);
if (!station) throw new Error(`Missing station ${code}`);
if (station.placementPrecision !== 'municipality_or_ward' || station.publishedAddressJa || station.facilityNameJa) {
  throw new Error(`Unexpected starting state for ${code}`);
}

console.log(`Hinode placement update: ${code} (${station.nameJa})`);
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
  '      "metadataStatus": "Official observation-locality label",',
  '      "placementPrecision": "municipality_or_ward"',
]) {
  if (!record.includes(expected)) throw new Error(`Unexpected record content for ${code}`);
}

const note = 'Hinode Town’s official competitive-bid record for its 2022 measurement-intensity-instrument replacement identifies the work site as Hinode Town Hall and publishes its Japanese address. The town has one current local-government observation point, 日の出町平井.';
let revised = record
  .replace('      "publishedAddressJa": null,', '      "publishedAddressJa": "東京都西多摩郡日の出町大字平井2780番地",')
  .replace('      "facilityNameJa": null,', '      "facilityNameJa": "日の出町役場",')
  .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official municipal seismic-meter placement",')
  .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"')
  .replace(/      "note": "[^"]*",/, `      "note": ${JSON.stringify(note)},`);
if (!revised.includes(evidenceSource)) {
  revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(evidenceSource)},`);
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));
console.log(`Applied Hinode placement update: ${code}`);
