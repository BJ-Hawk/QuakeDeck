import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.itoshima.lg.jp/s004/010/010/050/040/4syoujisinnsaigai.pdf';
const addressSource = 'https://itoshimalife.city.itoshima.lg.jp/help/';
const placements = new Map([
  ['4023031', ['糸島市交流プラザ二丈館', '福岡県糸島市二丈深江一丁目1番20号']],
  ['4023033', ['糸島市役所', '福岡県糸島市前原西一丁目1番1号']],
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
  station.note = 'The city disaster plan identifies the intensity meters at City Hall and Exchange Plaza Nijo Hall; the city publishes each facility address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, addressSource])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
