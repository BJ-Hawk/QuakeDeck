import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const updates = [
  ['1242732', '長南町長南', '長南町役場', '千葉県長生郡長南町長南2110番地', 'https://www.town.chonan.chiba.jp/shisetsu/87/'],
  ['1234230', '神崎町神崎本宿', '神崎町役場', '千葉県香取郡神崎町神崎本宿163番地', 'https://www.town.kozaki.chiba.jp/profile/access.html'],
].map(([code, nameJa, facilityNameJa, publishedAddressJa, addressSource]) => ({ code, nameJa, facilityNameJa, publishedAddressJa, addressSource }));
const data = JSON.parse(readFileSync(path, 'utf8'));
for (const update of updates) {
  const station = data.stations.find((entry) => entry.code === update.code);
  if (!station || station.prefectureJa !== '千葉県' || station.nameJa !== update.nameJa || station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) throw new Error(`Unexpected starting state for station ${update.code}.`);
  station.facilityNameJa = update.facilityNameJa;
  station.publishedAddressJa = update.publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official prefectural-network placement rule and published municipal facility address';
  station.sourceUrls = [...new Set([...station.sourceUrls, meterSource, update.addressSource])];
  station.note = `An official Chiba Prefecture network description states that its municipal intensity meters are installed in each municipality's main office building or grounds. The active station label matches ${update.facilityNameJa}'s published locality and address.`;
  delete station.placementLocalityJa;
}
data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
