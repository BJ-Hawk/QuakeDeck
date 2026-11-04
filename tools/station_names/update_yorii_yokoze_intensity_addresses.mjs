import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '1140830',
    nameJa: '寄居町寄居',
    facilityNameJa: '寄居町役場',
    facilityNameEn: 'Yorii Town Hall',
    publishedAddressJa: '埼玉県大里郡寄居町大字寄居1180番地1',
    placementLocalityJa: '寄居町大字寄居1180番地1',
    note: "Yorii Town's disaster plan, earthquake response chapter, printed page 212 (PDF page 240), explicitly identifies the intensity meter installed at the town hall. The official town-hall guide publishes its address as 寄居町大字寄居1180番地1. No more specific position within the site is asserted.",
    sourceUrls: [
      'https://www.town.yorii.saitama.jp/uploaded/attachment/23430.pdf',
      'https://www.town.yorii.saitama.jp/soshiki/02/yoriimachiyakuba.html',
    ],
  },
  {
    code: '1136130',
    nameJa: '横瀬町横瀬',
    facilityNameJa: '横瀬町役場',
    facilityNameEn: 'Yokoze Town Hall',
    publishedAddressJa: '埼玉県秩父郡横瀬町大字横瀬4545',
    placementLocalityJa: '横瀬町大字横瀬4545',
    note: "Yokoze Town's published disaster-plan materials draft (January 2026 public consultation), section 2-2(1), printed page 14, lists the existing intensity-meter measurement site as 横瀬町役場庁舎内 at 横瀬町大字横瀬4545. The town's consultation page independently gives the same town-hall address. The source is a published draft inventory, not a proposed relocation.",
    sourceUrls: [
      'https://www.town.yokoze.saitama.jp/wp-content/uploads/2026/01/%E6%A8%AA%E7%80%AC%E7%94%BA%E5%9C%B0%E5%9F%9F%E9%98%B2%E7%81%BD%E8%A8%88%E7%94%BB%EF%BC%88%E8%B3%87%E6%96%99%E7%B7%A8%EF%BC%89%E3%80%90%E6%A1%88%E3%80%91-.pdf',
      'https://www.town.yokoze.saitama.jp/yokoze/oshirase/61896',
    ],
  },
];

const original = fs.readFileSync(path, 'utf8');
const data = JSON.parse(original);
for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa) throw new Error(`Station identity mismatch: ${update.code}`);
  if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Unexpected existing placement: ${update.code}`);
  }
  const { code, nameJa, sourceUrls, ...fields } = update;
  Object.assign(station, fields, {
    metadataStatus: 'Official municipal facility placement',
    placementPrecision: 'exact_address',
  });
  for (const url of sourceUrls) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
const before = JSON.parse(original);
const allowedCodes = new Set(updates.map((item) => item.code));
for (let i = 0; i < before.stations.length; i += 1) {
  if (!allowedCodes.has(before.stations[i].code)
      && JSON.stringify(before.stations[i]) !== JSON.stringify(data.stations[i])) {
    throw new Error(`Unrelated station changed: ${before.stations[i].code}`);
  }
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Yorii/Yokoze intensity-address updates.`);
