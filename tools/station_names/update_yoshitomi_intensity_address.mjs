import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.town.yoshitomi.lg.jp/user/filer_public/50/8d/508d910c-03bf-47f5-943e-92eaec558b96/04_ji-fu-ting-di-yu-fang-zai-ji-hua-di-4zhang-di-zhen-jin-bo-ying-ji-dui-ce-ji-hua.pdf';
const addressSource = 'https://www.town.yoshitomi.lg.jp/gyosei/z378/';
const station = '4064230';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '吉富町役場';
  target.publishedAddressJa = '福岡県築上郡吉富町大字広津226番地1';
  target.metadataStatus = 'Official municipal facility address';
  target.note = 'The town disaster plan states that its intensity meter is installed at Yoshitomi Town Hall; the town publishes the hall address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, addressSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
