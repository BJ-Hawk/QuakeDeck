import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = [
  {
    code: '4421134', nameJa: '宇佐市上田',
    facilityNameJa: '宇佐市役所本庁舎', facilityNameEn: 'Usa City Hall Main Office',
    publishedAddressJa: '大分県宇佐市大字上田1030番地の1',
    placementLocalityJa: '宇佐市大字上田1030番地の1',
  },
  {
    code: '4421135', nameJa: '宇佐市安心院町',
    facilityNameJa: '宇佐市安心院支所', facilityNameEn: 'Usa City Ajimu Branch Office',
    publishedAddressJa: '大分県宇佐市安心院町下毛2115番地',
    placementLocalityJa: '宇佐市安心院町下毛2115番地',
  },
];
for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa) throw new Error(`Identity mismatch: ${update.code}`);
  if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Unexpected existing placement: ${update.code}`);
  }
  const { code, nameJa, ...placement } = update;
  Object.assign(station, placement, {
    placementPrecision: 'exact_address',
    metadataStatus: 'Official disaster-plan network placement and municipal facility address',
    note: "Usa City's earthquake/tsunami disaster plan, part 1 chapter 4, printed/PDF page 50, section 5(1)ア 震度計の設置, explicitly states that Oita Prefecture's intensity-network meters are installed at the main office and both branch offices (本庁と両支所に震度計が設置), covering the city's three regions. The municipal office guide separately identifies the main hall, Ajimu Branch and Innai Branch and publishes their individual addresses; the relevant main/Ajimu address is recorded here. The host identification comes from the comprehensive explicit meter-placement statement, not merely the station locality, an AMeDAS reference, or coordinate proximity. The already-addressed separate Innai record is not changed by this update. English facility names are descriptive translations. No floor, room, or precise within-site meter position is asserted.",
  });
  for (const url of [
    'https://www.city.usa.oita.jp/material/files/group/17/zisintunami1.pdf',
    'https://www.city.usa.oita.jp/sougo/shisei/gaiyo/3/7379.html',
  ]) {
    if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
  }
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Usa main/Ajimu intensity-address updates.`);
