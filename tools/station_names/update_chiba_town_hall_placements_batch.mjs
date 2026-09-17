import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const updates = [
  ['1240931', '芝山町小池', '芝山町役場', '千葉県山武郡芝山町小池992', 'https://www.town.shibayama.lg.jp/0000001804.html'],
  ['1240330', '九十九里町片貝', '九十九里町役場', '千葉県山武郡九十九里町片貝4099', 'https://www.pref.chiba.lg.jp/gyokou/tokuteizigyokeikaku/katagaikeikakujuuran.html'],
  ['1242230', '睦沢町下之郷', '睦沢町役場', '千葉県長生郡睦沢町下之郷1650-1', 'https://www.town.mutsuzawa.chiba.jp/akiya/no-507-%E7%9D%A6%E6%B2%A2%E7%94%BA%E4%B8%8A%E4%B9%8B%E9%83%B7'],
  ['1242330', '長生村本郷', '長生村役場', '千葉県長生郡長生村本郷1-77', 'https://www.vill.chosei.chiba.jp/soshiki_list.html'],
  ['1242431', '白子町関', '白子町役場', '千葉県長生郡白子町関5074-2', 'https://www.town.shirako.lg.jp/soshiki_list.html'],
  ['1242630', '長柄町桜谷', '長柄町役場', '千葉県長生郡長柄町桜谷712', 'https://www.town.nagara.chiba.jp/'],
].map(([code, nameJa, facilityNameJa, publishedAddressJa, addressSource]) => ({ code, nameJa, facilityNameJa, publishedAddressJa, addressSource }));
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const update of updates) {
  const station = data.stations.find((entry) => entry.code === update.code);
  if (!station || station.prefectureJa !== '千葉県' || station.nameJa !== update.nameJa || station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
    throw new Error(`Unexpected starting state for station ${update.code}.`);
  }
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
