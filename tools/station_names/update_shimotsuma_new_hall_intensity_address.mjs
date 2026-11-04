import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '0821033');
if (!station || station.nameJa !== '下妻市本城町') throw new Error('Shimotsuma station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Shimotsuma placement.');
}
Object.assign(station, {
  facilityNameJa: '下妻市役所',
  facilityNameEn: 'Shimotsuma City Hall',
  publishedAddressJa: '茨城県下妻市本城町三丁目13番地',
  metadataStatus: 'Official completed-relocation facility placement',
  note: "Shimotsuma City's fiscal 2023 major-policy results report, printed page 87 (PDF page 91), section 4 防災関連機器等移設事業, explicitly states that disaster-related equipment at the former main and second halls was moved to the new hall and lists completed prefectural intensity-meter equipment relocation costing 5,412,000 yen. The city's official new-hall opening notice publishes 茨城県下妻市本城町三丁目13番地 and confirms opening on 8 May 2023. This is completed relocation evidence, not an assumption from the station locality or a proposal; no within-site instrument position is asserted.",
  placementLocalityJa: '下妻市本城町三丁目13番地',
  placementPrecision: 'exact_address',
});
for (const url of [
  'https://www.city.shimotsuma.lg.jp/data/doc/1726800833_doc_149_0.pdf',
  'https://www.city.shimotsuma.lg.jp/shisei/kokyosisetsu/chosyaseibi/page005928.html',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Shimotsuma new-hall intensity-address update.`);
