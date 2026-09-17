import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const replaceSource = process.argv.includes('--replace-source');
const sourceUrl = 'https://www.pref.fukui.lg.jp/doc/dx-suishin/opendata/list_1_d/fil/zishinkeisettikasyo.csv';
const mirroredSourceUrl = 'https://linkdata.org/work/rdf1s2229i/zishinkeisetutikasyo.html?key=';
const updates = new Map([
  ['1820134', ['福井市小羽町27-1', '福井市役所旧清水総合支所']],
  ['1820533', ['大野市天神町1-1', '大野市役所']],
  ['1820731', ['鯖江市水落町2-25-28', '鯖江市文化の館']],
  ['1820832', ['あわら市市姫3-1-1', 'あわら市役所']],
  ['1820833', ['あわら市国影13-13', 'あわら市保健センター']],
  ['1832231', ['吉田郡永平寺町松岡春日1-4', '永平寺町役場']],
  ['1832233', ['吉田郡永平寺町東古市10-5', '永平寺町役場永平寺支所']],
  ['1832234', ['吉田郡永平寺町山王24-9', '上志比公民館']],
  ['1838230', ['今立郡池田町稲荷35-4', '池田町役場']],
  ['1840433', ['南条郡南越前町河野15-16-1', '南越前町役場河野事務所']],
  ['1840434', ['南条郡南越前町東大道29-1', '南越前町役場']],
  ['1840435', ['南条郡南越前町今庄84-25', '南越前町今庄事務所']],
  ['1842330', ['丹生郡越前町道口1-24-1', '越前町越前コミュニティーセンター']],
  ['1842334', ['丹生郡越前町織田153-1-8', '織田文化歴史館']],
  ['1842335', ['丹生郡越前町江波50-80-1', '越前町宮崎コミュニティーセンター']],
  ['1842337', ['丹生郡越前町西田中13-5-1', '越前町役場']],
  ['1844230', ['三方郡美浜町郷市25-25', '美浜町役場']],
  ['1848332', ['大飯郡おおい町名田庄久坂3-41-3', 'おおい町役場旧名田庄総合事務所']],
  ['1848333', ['大飯郡おおい町本郷136-1-1', 'おおい町役場']],
  ['1850131', ['三方上中郡若狭町市場20-18', '若狭町役場上中庁舎']],
  ['1850132', ['三方郡若狭町中央1-1', '若狭町役場三方庁舎']],
]);

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
if (replaceSource) {
  let output = raw;
  for (const [code] of updates) {
    const startMarker = Buffer.from(`    {\n      "code": "${code}",`, 'utf8');
    const start = output.indexOf(startMarker);
    if (start < 0) throw new Error(`Cannot find record ${code}`);
    const end = output.indexOf(Buffer.from('\n    },', 'utf8'), start);
    if (end < 0) throw new Error(`Cannot find end of record ${code}`);
    const record = output.subarray(start, end).toString('utf8');
    if (record.includes('\uFFFD') || !record.includes(mirroredSourceUrl) || !record.includes('"placementPrecision": "exact_address"')) {
      throw new Error(`Unexpected updated record content for ${code}`);
    }
    const revised = record.replace(mirroredSourceUrl, sourceUrl);
    output = Buffer.concat([output.subarray(0, start), Buffer.from(revised, 'utf8'), output.subarray(end)]);
  }
  fs.writeFileSync(inputPath, output);
  JSON.parse(fs.readFileSync(inputPath, 'utf8'));
  console.log(`Replaced mirrored provenance with Fukui's official CSV for ${updates.size} records.`);
  process.exit(0);
}
for (const [code] of updates) {
  const station = parsed.stations.find((candidate) => candidate.code === code);
  if (!station || station.placementPrecision !== 'municipality_or_ward' || station.publishedAddressJa !== null || station.facilityNameJa !== null) {
    throw new Error(`Unexpected starting state for ${code}`);
  }
}
console.log(`Fukui Prefecture earthquake-meter address update: ${updates.size} records`);
if (!apply) {
  console.log('Dry run only. Pass --apply to write the guarded updates.');
  process.exit(0);
}

let output = raw;
for (const [code, [address, facility]] of updates) {
  const startMarker = Buffer.from(`    {\n      "code": "${code}",`, 'utf8');
  const start = output.indexOf(startMarker);
  if (start < 0) throw new Error(`Cannot find record ${code}`);
  const end = output.indexOf(Buffer.from('\n    },', 'utf8'), start);
  if (end < 0) throw new Error(`Cannot find end of record ${code}`);
  const record = output.subarray(start, end).toString('utf8');
  if (record.includes('\uFFFD')) throw new Error(`Refusing to rewrite invalid UTF-8 in ${code}`);
  for (const expected of [
    '      "publishedAddressJa": null,',
    '      "facilityNameJa": null,',
    '      "placementPrecision": "municipality_or_ward"',
  ]) {
    if (!record.includes(expected)) throw new Error(`Unexpected record content for ${code}`);
  }
  const note = `Fukui Prefecture’s earthquake-meter installation table identifies this observation point at ${facility}, ${address}.`;
  let revised = record
    .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(address)},`)
    .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(facility)},`)
    .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Fukui Prefecture earthquake-meter installation table",')
    .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(note)},\n`)
    .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
  if (!revised.includes(sourceUrl)) {
    revised = revised.replace('      "sourceUrls": [', `      "sourceUrls": [\n        ${JSON.stringify(sourceUrl)},`);
  }
  output = Buffer.concat([output.subarray(0, start), Buffer.from(revised, 'utf8'), output.subarray(end)]);
}
fs.writeFileSync(inputPath, output);
JSON.parse(fs.readFileSync(inputPath, 'utf8'));
console.log(`Applied Fukui Prefecture earthquake-meter address update: ${updates.size} records`);
