import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const officialLabelUrl = 'https://www.data.jma.go.jp/eqev/data/intens-st/';
const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));

const candidates = parsed.stations.filter((station) =>
  station.placementPrecision !== 'exact_address'
  && station.nameJa
  && station.placementLocalityJa !== station.nameJa
  && !station.facilityNameJa
  && !station.publishedAddressJa
  && station.metadataStatus === 'Catalogue only'
  && station.note === 'No exact address or precise provider-station metadata is recorded yet.',
);

console.log(`Candidate locality refinements: ${candidates.length}`);
for (const station of candidates.slice(0, 12)) {
  console.log(`${station.code}: ${station.placementLocalityJa} -> ${station.nameJa}`);
}

if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded update.');
  process.exit(0);
}

let output = raw;
let applied = 0;
for (const station of candidates) {
  const startMarker = Buffer.from(`    {\n      "code": "${station.code}",`, 'utf8');
  const start = output.indexOf(startMarker);
  if (start < 0) throw new Error(`Cannot find record ${station.code}`);
  const end = output.indexOf(Buffer.from('\n    },', 'utf8'), start);
  if (end < 0) throw new Error(`Cannot find end of record ${station.code}`);

  const record = output.subarray(start, end).toString('utf8');
  if (record.includes('\uFFFD')) throw new Error(`Refusing to rewrite invalid UTF-8 in ${station.code}`);
  if (!record.includes(`      "placementLocalityJa": ${JSON.stringify(station.placementLocalityJa)},`)) {
    throw new Error(`Unexpected existing locality for ${station.code}`);
  }
  let revised = record
    .replace(
      `      "placementLocalityJa": ${JSON.stringify(station.placementLocalityJa)},`,
      `      "placementLocalityJa": ${JSON.stringify(station.nameJa)},`,
    )
    .replace(
      '      "metadataStatus": "Catalogue only",',
      '      "metadataStatus": "Official observation-locality label",',
    )
    .replace(
      '      "note": "No exact address or precise provider-station metadata is recorded yet.",',
      `      "note": "The official observation-point label identifies the locality ${station.nameJa}; it does not publish a host facility or street address.",`,
    );

  if (!revised.includes(officialLabelUrl)) {
    revised = revised.replace(
      '      "sourceUrls": [',
      `      "sourceUrls": [\n        ${JSON.stringify(officialLabelUrl)},`,
    );
  }

  if (revised === record) throw new Error(`No update made for ${station.code}`);
  output = Buffer.concat([output.subarray(0, start), Buffer.from(revised, 'utf8'), output.subarray(end)]);
  applied += 1;
}

fs.writeFileSync(inputPath, output);
console.log(`Applied ${applied} observation-label locality refinements.`);
