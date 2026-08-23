import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.iizuka.lg.jp/shobobosaianzen/bosai/bosai/shisaku/documents/chibouhonpen.pdf';
const addressSource = 'https://www.city.iizuka.lg.jp/shobobosaianzen/bosai/bosai/shisaku/documents/suibouhohnpen.pdf';
const placements = new Map([
  ['4020531', ['飯塚市役所筑穂支所', '福岡県飯塚市長尾1242番地1']],
  ['4020532', ['飯塚市役所穂波支所', '福岡県飯塚市忠隈523番地']],
  ['4020535', ['飯塚市役所庄内支所', '福岡県飯塚市綱分802番地7']],
  ['4020536', ['飯塚市役所本庁舎', '福岡県飯塚市新立岩5番5号']],
  ['4020537', ['飯塚市役所頴田支所', '福岡県飯塚市鹿毛馬2333番地4']],
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
  station.note = 'The city disaster plan states that intensity meters are installed at each city office building; its official address table identifies the corresponding main office and branch locations.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, addressSource])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
