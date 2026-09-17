import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const updates = [
  {
    code: '1221535',
    nameJa: '旭市ニ',
    facilityNameJa: '旭市役所',
    publishedAddressJa: '千葉県旭市ニの2132番地',
    addressSource: 'https://www.city.asahi.lg.jp/soshiki/3/2773.html',
  },
  {
    code: '1223735',
    nameJa: '山武市殿台',
    facilityNameJa: '山武市役所',
    publishedAddressJa: '千葉県山武市殿台296番地',
    addressSource: 'https://www.city.sammu.lg.jp/shisei/floorguide/page000006.html',
  },
  {
    code: '1223930',
    nameJa: '大網白里市大網',
    facilityNameJa: '大網白里市役所',
    publishedAddressJa: '千葉県大網白里市大網115番地2',
    addressSource: 'https://www.city.oamishirasato.lg.jp/0000002200.html',
  },
  {
    code: '1234931',
    nameJa: '東庄町笹川',
    facilityNameJa: '東庄町役場',
    publishedAddressJa: '千葉県香取郡東庄町笹川い4713番地131',
    addressSource: 'https://www.town.tohnosho.chiba.jp/soshiki/somuka/kikakuzaisei_kakari/gyomu/tonoshomachinogaiyo/635.html',
  },
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const update of updates) {
  const station = data.stations.find((entry) => entry.code === update.code);
  if (
    !station ||
    station.prefectureJa !== '千葉県' ||
    station.nameJa !== update.nameJa ||
    station.placementPrecision !== 'municipality_or_ward' ||
    station.facilityNameJa ||
    station.publishedAddressJa
  ) {
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
