import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '1320231';
const placementSource = 'https://www.city.tachikawa.lg.jp/_res/projects/default_project/_page_/001/006/602/00_zenbun.pdf';
const addressSource = 'https://www.city.tachikawa.lg.jp/faq/1020036/1002608/1002704.html';

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === code);
if (!station) throw new Error(`Missing station ${code}`);
if (station.placementPrecision !== 'municipality_or_ward' || station.publishedAddressJa || station.facilityNameJa) {
  throw new Error(`Unexpected starting state for ${code}`);
}

console.log(`Tachikawa placement update: ${code} (${station.nameJa})`);
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

const note = 'Tachikawa City’s official disaster plan identifies its measuring intensity meter as located on City Hall grounds. The current observation label 立川市泉町 matches the City Hall locality; the city publishes the facility’s address.';
let revised = record
  .replace('      "publishedAddressJa": null,', '      "publishedAddressJa": "東京都立川市泉町1156番地の9",')
  .replace('      "facilityNameJa": null,', '      "facilityNameJa": "立川市役所",')
  .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official municipal seismic-meter placement",')
  .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"')
  .replace(/      "note": "[^"]*",/, `      "note": ${JSON.stringify(note)},`);
if (!revised.includes(placementSource)) {
  revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(placementSource)},\n        ${JSON.stringify(addressSource)},`);
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));
console.log(`Applied Tachikawa placement update: ${code}`);
