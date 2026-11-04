import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const report = 'https://www.nilim.go.jp/lab/bbg/saigai/h23tohoku/110324kentiku2.pdf';
const updates = [
  {
    code: '0921130', nameJa: '矢板市本町',
    facilityNameJa: '矢板市役所', facilityNameEn: 'Yaita City Hall',
    publishedAddressJa: '栃木県矢板市本町5番4号',
    placementLocalityJa: '矢板市本町5番4号',
    note: "The joint NILIM/BRI April 2011 field report, section 4-1(7), printed/PDF page 4, explicitly locates Yaita's intensity meter at City Hall and documents it in photographs 31-32. The city's official City Hall facility page publishes 栃木県矢板市本町5番4号, corroborated by its October 2026 public-transport facility list. The report's east-side dry-area description is historical, not a verified current within-site position.",
    sourceUrls: [report,
      'https://www.city.yaita.tochigi.jp/map/map-yaitashiyakusho.html',
      'https://www.city.yaita.tochigi.jp/soshiki/toshiseibi/demand-shisetsu.html'],
  },
  {
    code: '0940730', nameJa: '那須町寺子',
    facilityNameJa: '那須町役場本庁舎', facilityNameEn: 'Nasu Town Hall Main Office',
    publishedAddressJa: '栃木県那須郡那須町大字寺子丙3-13',
    placementLocalityJa: '那須町大字寺子丙3-13',
    note: "The joint NILIM/BRI April 2011 field report, section 4-1(2), printed/PDF page 2, explicitly places Nasu's intensity meter beside the town-hall building and documents it in photograph 5. The town's current main-office guide publishes 栃木県那須郡那須町大字寺子丙3-13. The report's west-side planted-bed description is historical, not a verified current within-site position.",
    sourceUrls: [report, 'https://www.town.nasu.lg.jp/viewer/info.html?id=812&idSubTop=1'],
  },
  {
    code: '0921430', nameJa: '栃木さくら市氏家',
    facilityNameJa: 'さくら市役所本庁舎', facilityNameEn: 'Sakura City Hall Main Office',
    publishedAddressJa: '栃木県さくら市氏家2771番地',
    placementLocalityJa: 'さくら市氏家2771番地',
    note: "The joint NILIM/BRI April 2011 field report, section 4-2(6), printed/PDF page 11, explicitly identifies the intensity meter at Sakura City Hall, with photographs 74-75. The city's current main-office guide publishes 栃木県さくら市氏家2771番地. The report's elevator-hall/main-building position is historical, not a verified current within-site position; the separate Kitsuregawa station is not assigned from this evidence.",
    sourceUrls: [report, 'https://www.city.tochigi-sakura.lg.jp/government/000051/000302/p000100.html'],
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
    metadataStatus: 'Official national field-survey facility placement',
    placementPrecision: 'exact_address',
  });
  for (const url of sourceUrls) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Yaita/Nasu/Sakura intensity-address updates.`);
