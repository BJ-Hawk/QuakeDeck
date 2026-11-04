import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const specification = 'https://www.city.shimotsuke.lg.jp/manage/contents/upload/614b092478fa9.pdf';
const updates = [
  {
    code: '0921633', nameJa: '下野市田中',
    facilityNameJa: '下野市立南河内図書館', facilityNameEn: 'Minamikawachi Library',
    publishedAddressJa: '栃木県下野市田中681番地1',
    placementLocalityJa: '下野市田中681番地1',
    addressPage: 'https://www.city.shimotsuke.lg.jp/1895/info-0000001974-3.html',
    addressEvidence: 'printed page 2 (PDF page 4) identifies Minamikawachi Library at 下野市田中681番地1',
  },
  {
    code: '0921635', nameJa: '下野市大松山',
    facilityNameJa: '下野市立石橋図書館', facilityNameEn: 'Ishibashi Library',
    publishedAddressJa: '栃木県下野市大松山一丁目7番3号',
    placementLocalityJa: '下野市大松山一丁目7番3号',
    addressPage: 'https://www.city.shimotsuke.lg.jp/0673/info-0000001536-3.html',
    addressEvidence: 'printed page 1 (PDF page 3) identifies Ishibashi Library at 下野市大松山一丁目7番3号',
  },
];
for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa) throw new Error(`Identity mismatch: ${update.code}`);
  if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Unexpected existing placement: ${update.code}`);
  }
  const { code, nameJa, addressPage, addressEvidence, ...placement } = update;
  Object.assign(station, placement, {
    metadataStatus: 'Official library instrument inventory and published facility address',
    placementPrecision: 'exact_address',
    note: `Shimotsuke City Board of Education's September 2021 library-management specification, for the 1 April 2022 to 31 March 2027 management term, printed page 11 (PDF page 13), section 16 計測震度計の管理, explicitly identifies one installed instrumental intensity meter at each of Ishibashi Library and Minamikawachi Library and requires their inspection and management. In the same specification, ${addressEvidence}; the city's current library facility page independently publishes the same address. The host identification comes from the explicit instrument inventory, not merely the station locality or facility proximity. The separate Sasahara station is not assigned from this evidence, and no within-site instrument position is asserted.`,
  });
  for (const url of [specification, addressPage]) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Shimotsuke library intensity-address updates.`);
