import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const report = 'https://www.city.inabe.mie.jp/_res/projects/default_project/_page_/001/014/543/r6zimuzigyouhyouka02.pdf';
const updates = [
  {
    code: '2421435', nameJa: 'いなべ市員弁町笠田新田',
    facilityNameJa: 'いなべ市消防団員弁地区団第1分団詰所',
    facilityNameEn: 'Inabe City Fire Brigade, Inabe First Division Station',
    publishedAddressJa: '三重県いなべ市員弁町笠田新田111',
    placementLocalityJa: 'いなべ市員弁町笠田新田111',
    addressEvidence: 'The same report, printed page 38 (PDF page 52), section 8 item 1(2), identifies the Inabe district First Division building at 員弁町笠田新田111.',
  },
  {
    code: '2421436', nameJa: 'いなべ市大安町丹生川久下',
    facilityNameJa: 'いなべ市消防団大安地区団北分団詰所',
    facilityNameEn: 'Inabe City Fire Brigade, Daian North Division Station',
    publishedAddressJa: '三重県いなべ市大安町丹生川久下2386-1',
    placementLocalityJa: 'いなべ市大安町丹生川久下2386-1',
    addressEvidence: 'The same report, printed page 38 (PDF page 52), section 8 item 1(3), identifies the Daian district North Division building at 大安町丹生川久下2386-1. This contemporary address supersedes 2808 in the older brigade inventory; no old-site address is assigned.',
  },
  {
    code: '2421438', nameJa: 'いなべ市北勢町阿下喜',
    facilityNameJa: 'いなべ市役所本庁舎',
    facilityNameEn: 'Inabe City Hall Main Office',
    publishedAddressJa: '三重県いなべ市北勢町阿下喜31番地',
    placementLocalityJa: 'いなべ市北勢町阿下喜31番地',
    addressEvidence: 'The city\'s access guide, updated 28 March 2025, directly publishes the current City Hall address 北勢町阿下喜31番地 and distinguishes City Hall from the separate Hokusei office. The instrument-fee list names 本庁舎; its separate television-fee list distinguishes 本庁舎及び北勢庁舎, so the former Hokusei office address is not substituted.',
    addressUrl: 'https://www.city.inabe.mie.jp/shisei/aboutinabe/shiyakusho/1007668.html',
  },
];

for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa) throw new Error(`Identity mismatch: ${update.code}`);
  if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Unexpected existing placement: ${update.code}`);
  }
  const { code, nameJa, addressEvidence, addressUrl, ...placement } = update;
  Object.assign(station, placement, {
    placementPrecision: 'exact_address',
    metadataStatus: 'Official instrument line-fee inventory and published facility address',
    note: `Inabe City's fiscal 2024 project evaluation, prepared 25 July 2025, printed page 30 (PDF page 44), section 8 item 5 地震計（震度計）回線使用料, explicitly identifies instrument communication lines at the main hall, Daian North Division building, Inabe First Division building, and Fujiwara First Division building. These three uniquely identified hosts match the respective current observation-point localities; the host evidence is the explicit instrument inventory, not coordinate proximity or an address match alone. ${addressEvidence} The separate Fujiwara station is not assigned from this list because the brigade inventory names two First Division sites. English facility labels are descriptive translations of the published Japanese identities, not claimed official English titles. No current within-site instrument position is asserted.`,
  });
  for (const url of [report, ...(addressUrl ? [addressUrl] : [])]) {
    if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
  }
}

if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Inabe intensity-address updates.`);
