import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const municipalPlan = 'https://www.town.nagashima.lg.jp/wp-content/uploads/2022/12/%E9%95%B7%E5%B3%B6%E7%94%BA%E5%85%AC%E5%85%B1%E6%96%BD%E8%A8%AD%E7%AD%89%E7%B7%8F%E5%90%88%E7%AE%A1%E7%90%86%E8%A8%88%E7%94%BB%E7%89%8820221207.pdf';
const updates = new Map([
  ['4640431', 'Kagoshima Prefecture\'s published seismic-intensity maintenance table places this meter on the grounds of Nagashima Town Shishijima Community Center. Nagashima Town\'s public-facilities plan separately records that facility as dismantled in fiscal 2021; neither source identifies a replacement meter facility or a parcel address.'],
  ['4640434', 'Kagoshima Prefecture\'s published seismic-intensity maintenance table places this meter on the grounds of Nagashima Town Ikarajima Community Center. Nagashima Town\'s public-facilities plan separately records that facility as dismantled in fiscal 2021; neither source identifies a replacement meter facility or a parcel address.'],
]);

const data = JSON.parse(fs.readFileSync(path, 'utf8'));
let changed = 0;
for (const station of data.stations) {
  const note = updates.get(station.code);
  if (!note) continue;
  if (station.note !== note || !station.sourceUrls.includes(municipalPlan) || station.metadataStatus !== 'Official prefectural facility placement; current-status conflict') {
    station.note = note;
    station.metadataStatus = 'Official prefectural facility placement; current-status conflict';
    if (!station.sourceUrls.includes(municipalPlan)) station.sourceUrls.push(municipalPlan);
    changed++;
  }
}
if (changed !== updates.size) throw new Error(`Expected ${updates.size} changes, got ${changed}.`);
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${changed} Nagashima island facility-status conflict record updates.`);
