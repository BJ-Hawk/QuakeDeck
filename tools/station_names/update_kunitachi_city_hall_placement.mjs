import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '1321530';
const placementSource = 'https://www.city.kunitachi.tokyo.jp/material/files/group/20/03_Sinsai_Oukyu.pdf';
const addressSource = 'https://www.city.kunitachi.tokyo.jp/kurashi/zeikin/3/11887.html';

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === code);
if (!station) throw new Error(`Missing station ${code}`);
if (station.placementPrecision !== 'municipality_or_ward' || station.publishedAddressJa || station.facilityNameJa) {
  throw new Error(`Unexpected starting state for ${code}`);
}

console.log(`Kunitachi placement update: ${code} (${station.nameJa})`);
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

const note = 'Kunitachi City’s official disaster plan identifies its measuring intensity meter as located on City Hall grounds. The current observation label 国立市富士見台 matches the City Hall locality; the city publishes the facility’s address.';
let revised = record
  .replace('      "publishedAddressJa": null,', '      "publishedAddressJa": "東京都国立市富士見台2-47-1",')
  .replace('      "facilityNameJa": null,', '      "facilityNameJa": "国立市役所",')
  .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official municipal seismic-meter placement",')
  .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"')
  .replace(/      "note": "[^"]*",/, `      "note": ${JSON.stringify(note)},`);
if (!revised.includes(placementSource)) {
  revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(placementSource)},\n        ${JSON.stringify(addressSource)},`);
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));
console.log(`Applied Kunitachi placement update: ${code}`);
