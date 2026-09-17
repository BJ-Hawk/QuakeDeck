import fs from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const observatorySource = 'https://www.bousai.city.kyoto.lg.jp/cmsfiles/contents/0000000/148/siryou1.pdf';
const addressSource = 'https://www.city.kyoto.lg.jp/gyozai/cmsfiles/contents/0000287/287682/R07_shisetsu.pdf';

const updates = [
  ['2610440', '京都中京区河原町御池', '消防局本部庁舎', 'Kyoto City Fire Bureau Headquarters', '京都府京都市中京区押小路通河原町西入榎木町450番地の2'],
  ['2610540', '京都東山区清水', '東山消防署', 'Higashiyama Fire Station', '京都府京都市東山区清水五丁目130番地の8'],
  ['2610640', '京都下京区河原町塩小路', '塩小路消防出張所', 'Shiokoji Fire Substation', '京都府京都市下京区上之町13番地'],
  ['2610740', '京都南区西九条', '南消防署', 'Minami Fire Station', '京都府京都市南区西九条菅田町4番地の1'],
  ['2610841', '京都右京区太秦', '右京消防署', 'Ukyo Fire Station', '京都府京都市右京区太秦蜂岡町36番地'],
  ['2610940', '京都伏見区竹田', '伏見消防署', 'Fushimi Fire Station', '京都府京都市伏見区竹田七瀬川町9番地の1'],
  ['2610941', '京都伏見区醍醐', '醍醐消防分署', 'Daigo Fire Substation', '京都府京都市伏見区醍醐大構町28番地'],
  ['2611040', '京都山科区西野', '山科消防署', 'Yamashina Fire Station', '京都府京都市山科区西野今屋敷町2番地の10'],
  ['2611140', '京都西京区樫原', '西京消防署', 'Nishikyo Fire Station', '京都府京都市西京区樫原佃19番地'],
  ['2611141', '京都西京区大枝', '洛西消防出張所', 'Rakusai Fire Substation', '京都府京都市西京区大枝東新林町二丁目4番地'],
].map(([code, nameJa, facilityNameJa, facilityNameEn, address]) => ({code, nameJa, facilityNameJa, facilityNameEn, address}));

const raw = fs.readFileSync(inputPath);
const parsed = JSON.parse(raw.toString('utf8'));
for (const update of updates) {
  const station = parsed.stations.find((candidate) => candidate.code === update.code);
  if (!station || station.nameJa !== update.nameJa || station.placementPrecision !== 'municipality_or_ward' ||
      station.publishedAddressJa !== null || station.facilityNameJa !== null) {
    throw new Error(`Unexpected starting state for ${update.code}`);
  }
}
console.log(`Kyoto City observatory updates: ${updates.map((update) => update.code).join(', ')}`);
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
  for (const expected of ['      "publishedAddressJa": null,', '      "facilityNameJa": null,', '      "placementPrecision": "municipality_or_ward"']) {
    if (!record.includes(expected)) throw new Error(`Unexpected record content for ${update.code}`);
  }
  const note = 'Kyoto City’s disaster-plan appendix identifies this locality’s seismic observatory at the named facility; the city’s official facility register publishes the address.';
  let revised = record
    .replace('      "publishedAddressJa": null,', `      "publishedAddressJa": ${JSON.stringify(update.address)},`)
    .replace('      "facilityNameJa": null,', `      "facilityNameJa": ${JSON.stringify(update.facilityNameJa)},`)
    .replace('      "facilityNameEn": null,', `      "facilityNameEn": ${JSON.stringify(update.facilityNameEn)},`)
    .replace('      "metadataStatus": "Official observation-locality label",', '      "metadataStatus": "Official municipal disaster-plan observatory placement and published facility address",')
    .replace(/      "note": .*?,\n/, `      "note": ${JSON.stringify(note)},\n`)
    .replace('      "placementPrecision": "municipality_or_ward"', '      "placementPrecision": "exact_address"');
  for (const source of [observatorySource, addressSource]) {
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
      !station.sourceUrls.includes(observatorySource) || !station.sourceUrls.includes(addressSource)) {
    throw new Error(`Post-write validation failed for ${update.code}`);
  }
}
console.log(`Applied Kyoto City observatory updates: ${updates.map((update) => update.code).join(', ')}`);
