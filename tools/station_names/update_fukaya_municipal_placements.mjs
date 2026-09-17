import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const placementSource = 'https://www.city.fukaya.saitama.jp/material/files/group/8/3_oukyuu.pdf';
const officeSource = 'https://www.city.fukaya.saitama.jp/soshiki/kikakuzaisei/kikaku/tanto/1391680776859.html';
const headquartersSource = 'https://www.city.fukaya.saitama.jp/soshiki/somu/somu/tanto/cyousyakannkei/16384.html';

const updates = {
  '1121834': {
    address: '埼玉県深谷市小前田2345-1',
    facility: '深谷市役所花園総合支所',
    source: officeSource,
  },
  '1121835': {
    address: '埼玉県深谷市普済寺1626-3',
    facility: '深谷市役所岡部総合支所',
    source: officeSource,
  },
  '1121836': {
    address: '埼玉県深谷市仲町11番地1',
    facility: '深谷市役所本庁舎',
    source: headquartersSource,
  },
  '1121837': {
    address: '埼玉県深谷市菅沼401',
    facility: '深谷市役所川本総合支所',
    source: officeSource,
  },
};

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
for (const code of Object.keys(updates)) {
  const station = parsed.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.placementPrecision !== 'municipality_or_ward' || station.publishedAddressJa || station.facilityNameJa) {
    throw new Error(`Unexpected starting state for ${code}`);
  }
}

console.log(`Fukaya placement updates: ${Object.keys(updates).join(', ')}`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded update.');
  process.exit(0);
}

let output = raw;
for (const [code, update] of Object.entries(updates)) {
  const startMarker = Buffer.from(`    {\n      "code": "${code}",`, 'utf8');
  const start = output.indexOf(startMarker);
  if (start < 0) throw new Error(`Cannot find record ${code}`);
  const end = output.indexOf(Buffer.from('\n    },', 'utf8'), start);
  if (end < 0) throw new Error(`Cannot find end of record ${code}`);

  const record = output.subarray(start, end).toString('utf8');
  if (record.includes('\uFFFD')) throw new Error(`Refusing to rewrite invalid UTF-8 in ${code}`);
  const expected = [
    '      "publishedAddressJa": null,',
    '      "facilityNameJa": null,',
    '      "metadataStatus": "Official observation-locality label",',
    '      "placementPrecision": "municipality_or_ward"',
  ];
  if (expected.some((line) => !record.includes(line))) {
    throw new Error(`Unexpected record content for ${code}`);
  }

  const note = 'Fukaya City\'s official plan identifies its four prefectural-network meters as City Hall and each of its three General Branch Offices; the four current observation labels correspond one-to-one to those four host locations. The city publishes this host address.';
  let revised = record
    .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(update.address)},`)
    .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(update.facility)},`)
    .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official municipal seismic-meter placement",')
    .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"')
    .replace(/      "note": "[^"]*",/, `      "note": ${JSON.stringify(note)},`);
  if (!revised.includes(placementSource)) {
    revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(placementSource)},\n        ${JSON.stringify(update.source)},`);
  }
  output = Buffer.concat([output.subarray(0, start), Buffer.from(revised, 'utf8'), output.subarray(end)]);
}

fs.writeFileSync(inputPath, output);
console.log(`Applied ${Object.keys(updates).length} Fukaya municipal placement updates.`);
