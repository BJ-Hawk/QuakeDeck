import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '2021532';
const address = '長野県塩尻市木曽平沢2221番地';
const addressSource = 'https://www.city.shiojiri.lg.jp/site/narakawachiku/';

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
const station = parsed.stations.find((candidate) => candidate.code === code);
if (!station || station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa !== '旧楢川支所' || station.publishedAddressJa !== null) {
  throw new Error(`Unexpected starting state for ${code}`);
}
console.log(`Shiojiri former-Narakawa-Branch address update: ${code} (${station.nameJa})`);
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
  '      "facilityNameJa": "旧楢川支所",',
  '      "placementPrecision": "municipality_or_ward"',
]) {
  if (!record.includes(expected)) throw new Error(`Unexpected record content for ${code}`);
}

const note = 'Nagano Prefecture identifies this seismic-intensity observation point at the former Narakawa Branch Office. Shiojiri City’s official district page identifies that former branch-office site at Kiso-Hirasawa 2221.';
let revised = record
  .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(address)},`)
  .replace('      "metadataStatus": "source_verified",', '      "metadataStatus": "Official prefectural seismic-meter placement and municipal historical-site address",')
  .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(note)},\n`)
  .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
if (!revised.includes(addressSource)) {
  revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(addressSource)},`);
}
fs.writeFileSync(inputPath, Buffer.concat([raw.subarray(0, start), Buffer.from(revised, 'utf8'), raw.subarray(end)]));
console.log(`Applied Shiojiri former-Narakawa-Branch address update: ${code}`);
