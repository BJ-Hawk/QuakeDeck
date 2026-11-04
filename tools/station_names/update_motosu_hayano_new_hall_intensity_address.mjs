import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '2121835');
if (!station || station.nameJa !== '本巣市早野') throw new Error('Motosu Hayano station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Motosu Hayano placement.');
}
Object.assign(station, {
  facilityNameJa: '本巣市役所本庁舎',
  facilityNameEn: 'Motosu City Hall Main Office',
  publishedAddressJa: '岐阜県本巣市早野255番地',
  placementLocalityJa: '本巣市早野255番地',
  placementPrecision: 'exact_address',
  metadataStatus: 'Installer-specific meter relocation record, official JMA code continuity and municipal address',
  note: "Wakoh Tsushin's primary work-history table lists Motosu City Hall's specific 岐阜県震度計（本巣市三橋）設備移設工事, describes relocation of the prefectural intensity-information network associated with the office move, and gives the work period August 2023–September 2024. This is a specific meter-relocation job, distinct from the separately listed general prefectural disaster-communications relocation, not the contractor's generic claim about meters at municipal halls. JMA's 14 February 2024 XML/code-table update, appendix 1 section 2, PDF page 3, independently pairs the relocated former Mitsuhashi station 2121832 with Hayano station 2121835. Motosu City's new-hall opening guide, dated 16 July 2024, confirms opening of the replacement main hall and explicitly publishes 本巣市早野255番地, with the full prefectural address in the municipal contact block. Taken together, the specific contractor relocation record, official old/new station-code link, and municipal new-hall guide identify the Hayano host; no single source is claimed to publish the entire code/facility/address mapping. No exact meter-transfer date is inferred from the announced code-table change, office-opening date, or overall construction period. The former Mitsuhashi office address is not used, and the separate Monju, Neo and Shimomakuwa station records are unchanged. No room, floor, or precise within-site meter position is asserted. The English facility label is descriptive, not claimed as an official English title.",
});
for (const url of [
  'https://wakoh-t.com/works/',
  'https://xml.kishou.go.jp/20240214_jmaxml_info.pdf',
  'https://www.city.motosu.lg.jp/0000002550.html',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Motosu Hayano new-hall intensity-address update.`);
