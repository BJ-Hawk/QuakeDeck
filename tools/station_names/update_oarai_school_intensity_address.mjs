import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '0830931');
if (!station || station.nameJa !== '大洗町磯浜町') throw new Error('Oarai station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Oarai placement.');
}
Object.assign(station, {
  facilityNameJa: '大洗町立大洗小学校',
  facilityNameEn: 'Oarai Elementary School',
  publishedAddressJa: '茨城県東茨城郡大洗町磯浜町5316-1',
  metadataStatus: 'Official completed-relocation facility placement',
  note: "Oarai Town's 5 July 2023 newsletter, printed/PDF page 18, explicitly reports that the town's intensity meter was moved from the fire-headquarters grounds to the Oarai Elementary School grounds and has operated there since June 2023, after coordination with Ibaraki Prefecture and JMA. The town's official primary-school directory publishes 大洗町磯浜町5316-1 for Oarai Elementary School. This completed-move notice supersedes the May 2022 newsletter and June 2022 council record describing the former fire-headquarters placement; the old fire-headquarters address is not used. No position within the school grounds is inferred.",
  placementLocalityJa: '大洗町磯浜町5316-1',
  placementPrecision: 'exact_address',
});
for (const url of [
  'https://www.town.oarai.lg.jp/wp/wp-content/uploads/2023/06/%E5%BA%83%E5%A0%B1%E3%81%8A%E3%81%8A%E3%81%82%E3%82%89%E3%81%847%E6%9C%88%E5%8F%B7-%E6%9C%80%E7%B5%82.pdf',
  'https://www.town.oarai.lg.jp/kosodatekyouiku/shouchuugakkou/shouchuu/1332/',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Oarai school intensity-address update.`);
