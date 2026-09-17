import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const updates = [
  ['1222531', '君津市久保', '君津市役所', '千葉県君津市久保2丁目13番1号', 'https://www.city.kimitsu.lg.jp/uploaded/attachment/59355.pdf'],
  ['1222631', '富津市下飯野', '富津市役所', '千葉県富津市下飯野2443番地', 'https://www.city.futtsu.lg.jp/0000001349.html'],
  ['1222931', '袖ケ浦市坂戸市場', '袖ケ浦市役所', '千葉県袖ケ浦市坂戸市場1番地1', 'https://www.city.sodegaura.lg.jp/soshiki/zaisei/invoice-number.html'],
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
