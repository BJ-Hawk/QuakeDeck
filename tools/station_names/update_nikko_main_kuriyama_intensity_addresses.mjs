import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const expenditure = 'https://www.city.nikko.lg.jp/material/files/group/6/kessanmeisaisyo_H30.pdf';
const updates = [
  {
    code: '0920639', nameJa: '日光市今市本町',
    facilityNameJa: '日光市役所本庁舎', facilityNameEn: 'Nikko City Hall Main Office',
    publishedAddressJa: '栃木県日光市今市本町1番地',
    placementLocalityJa: '日光市今市本町1番地',
    note: "Nikko City's fiscal 2018 expenditure detail, printed pages 150-151 (PDF pages 164-165), records main-hall construction and system relocation expenditure, explicitly including 本庁舎計測震度計移設業務委託 under the new-hall relocation work. The 1,738,800-yen line includes other work and is not represented as the meter's individual cost. The city's completed main-hall project page confirms operation in the new hall began on 4 January 2019 after construction and relocation; its current main-office guide publishes 栃木県日光市今市本町1番地. This identifies the local-government Imaichi Honcho station through instrument relocation evidence, not proximity or the address alone. No current within-site instrument position is asserted.",
    sourceUrls: [expenditure,
      'https://www.city.nikko.lg.jp/torikumi/gyozaisei/5566.html',
      'https://www.city.nikko.lg.jp/soshiki/1/1002/15/1/1361.html'],
  },
  {
    code: '0920638', nameJa: '日光市黒部',
    facilityNameJa: '日光市栗山庁舎（栗山行政センター）', facilityNameEn: 'Kuriyama Administrative Center',
    publishedAddressJa: '栃木県日光市黒部54番地1',
    placementLocalityJa: '日光市黒部54番地1',
    note: "Nikko City's fiscal 2018 expenditure detail, printed pages 173-174 (PDF pages 189-190), identifies the Kuriyama Administrative Center section and records completed new Kuriyama hall construction and relocation expenditure, including 計測震度計移設業務委託 (2,322,000 yen) and 計測震度計室内配線移設工事 (49,680 yen). The city's current Kuriyama hall guide directly identifies the Administrative Center at 日光市黒部54番地1. This is executed instrument-relocation expenditure at the new host hall, not the former Hikage site or a proposed relocation. No within-site instrument position is inferred from the indoor-wiring work description.",
    sourceUrls: [expenditure,
      'https://www.city.nikko.lg.jp/soshiki/1/1002/15/1/2296.html'],
  },
];
for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa) throw new Error(`Identity mismatch: ${update.code}`);
  if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Unexpected existing placement: ${update.code}`);
  }
  const { code, nameJa, sourceUrls, ...placement } = update;
  Object.assign(station, placement, {
    metadataStatus: 'Official executed-relocation expenditure and published facility address',
    placementPrecision: 'exact_address',
  });
  for (const url of sourceUrls) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Nikko main/Kuriyama intensity-address updates.`);
