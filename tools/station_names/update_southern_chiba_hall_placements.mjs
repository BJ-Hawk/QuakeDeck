import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const updates = [
  ['1223430', '南房総市富浦町青木', '南房総市役所', '千葉県南房総市富浦町青木28番地', 'https://www.city.minamiboso.chiba.jp/0000002838.html'],
  ['1244130', '大多喜町大多喜', '大多喜町役場', '千葉県夷隅郡大多喜町大多喜93', 'https://www.town.otaki.chiba.jp/soshiki/soumu/3/1/7/1/825.html'],
  ['1244330', '御宿町須賀', '御宿町役場', '千葉県夷隅郡御宿町須賀1522', 'https://www.town.onjuku.chiba.jp/sub1/5/51.html'],
  ['1246330', '鋸南町下佐久間', '鋸南町役場', '千葉県安房郡鋸南町下佐久間3458番地', 'https://www.town.kyonan.chiba.jp/soshiki/6/0013687.html'],
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
