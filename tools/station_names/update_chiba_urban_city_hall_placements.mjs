import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const updates = [
  ['1220332', '市川市八幡', '市川市役所', '千葉県市川市八幡1丁目1番1号', 'https://www.city.ichikawa.lg.jp/page/4647.html'],
  ['1221632', '習志野市鷺沼', '習志野市役所', '千葉県習志野市鷺沼2丁目1番1号', 'https://www.city.narashino.lg.jp/soshiki/keiyakukensa/gyomu/shisetu/shi/shi.html'],
  ['1222131', '八千代市大和田新田', '八千代市役所', '千葉県八千代市大和田新田312-5', 'https://www.city.yachiyo.lg.jp/soshiki/18/2699.html'],
  ['1222732', '浦安市猫実', '浦安市役所', '千葉県浦安市猫実1丁目1番1号', 'https://www.city.urayasu.lg.jp/todokede/kankyo/torikumi/1042825.html'],
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
