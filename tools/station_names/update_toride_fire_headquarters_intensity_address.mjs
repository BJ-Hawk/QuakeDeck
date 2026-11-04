import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '0821730');
if (!station || station.nameJa !== '取手市井野') throw new Error('Toride station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Toride placement.');
}
Object.assign(station, {
  facilityNameJa: '取手市消防本部',
  facilityNameEn: 'Toride Fire Headquarters',
  publishedAddressJa: '茨城県取手市井野1264番地1',
  metadataStatus: 'Official fire-service instrument placement',
  note: "Toride Fire Headquarters' 2023 annual report (published July 2024), printed page 50, explicitly records the prefecture-installed intensity meter on the headquarters building's first floor, installed in June 2001. Its printed page 20 identifies the headquarters/Toride Fire Station building at 取手市井野1264番地1, completed on 1 April 1989. The national Fire and Disaster Management Agency's current headquarters directory independently publishes the same Ino 1264-1 address. This direct headquarters-instrument record identifies the Ino local-government station; the other Toride stations are not assigned from it. The first-floor position is the report's installation-history description, not a new inspection of the instrument's current within-building position.",
  placementLocalityJa: '取手市井野1264番地1',
  placementPrecision: 'exact_address',
});
for (const url of [
  'https://www.city.toride.ibaraki.jp/shobo-somu/kurashi/anzen/shobo/shobonenpo/documents/r5nennpou.pdf',
  'https://www.fdma.go.jp/relocation/josei_shokuin/previous/honbusaerch/080004_ibaragi/31514.html',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Toride fire-headquarters intensity-address update.`);
