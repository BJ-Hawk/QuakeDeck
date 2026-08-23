import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.yanagawa.fukuoka.jp/fs/4/7/4/9/_/file20230607132701.pdf';
const addressSource = 'https://www.city.yanagawa.fukuoka.jp/shisei/shinogaiyo/choshaannai/';
const placements = new Map([
  ['4020731', ['柳川市大和庁舎', '福岡県柳川市大和町鷹ノ尾120番地']],
  ['4020732', ['柳川市三橋庁舎', '福岡県柳川市三橋町正行431番地']],
  ['4020733', ['柳川市柳川庁舎', '福岡県柳川市本町87番地1']],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  const placement = placements.get(station.code);
  if (!placement || station.publishedAddressJa || station.facilityNameJa) continue;
  const [facility, address] = placement;
  station.facilityNameJa = facility;
  station.publishedAddressJa = address;
  station.metadataStatus = 'Official municipal facility address';
  station.note = 'The city disaster plan identifies the intensity meters at the Yanagawa, Yamato, and Mitsuhashi office buildings; the city publishes each building address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, addressSource])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
