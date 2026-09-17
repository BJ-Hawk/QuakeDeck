import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const updates = [
  ['1221732', '柏市柏', '柏市役所', '千葉県柏市柏5丁目10番1号', 'https://www.city.kashiwa.lg.jp/facilities/cityhall/cityhall.html'],
  ['1222431', '鎌ケ谷市新鎌ケ谷', '鎌ケ谷市役所', '千葉県鎌ケ谷市新鎌ケ谷2丁目6番1号', 'https://www.city.kamagaya.chiba.jp/kenko-fukushi/kenko-iryo/sonohoka/nettyusyo/shelter.files/map.pdf'],
  ['1222831', '四街道市鹿渡', '四街道市役所', '千葉県四街道市鹿渡無番地', 'https://www.city.yotsukaido.chiba.jp/shisei/saiyo/ninnkitukihoikushi.html'],
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
