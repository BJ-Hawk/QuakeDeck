import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.kurume.fukuoka.jp/1050kurashi/2040bousaianzen/3003shiru/files/05siryouhenn.pdf';
const addressSource = 'https://www.city.kurume.fukuoka.jp/1090sangyou/2010nyuusatsu/3100bid/files/dennwa_siyousyo.pdf';
const placements = new Map([
  ['4020330', ['久留米市役所', '福岡県久留米市城南町15番地3']],
  ['4020333', ['久留米市役所城島総合支所', '福岡県久留米市城島町楢津743番地2']],
  ['4020334', ['久留米市役所三潴総合支所', '福岡県久留米市三潴町玉満2779番地1']],
  ['4020336', ['久留米市役所北野総合支所', '福岡県久留米市北野町中3245番地3']],
  ['4020337', ['久留米市役所田主丸総合支所', '福岡県久留米市田主丸町田主丸459番地11']],
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
  station.note = 'The city’s Fukuoka network agreement confirms meters at the main office and four general branch offices; the city publishes each office address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, addressSource])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
