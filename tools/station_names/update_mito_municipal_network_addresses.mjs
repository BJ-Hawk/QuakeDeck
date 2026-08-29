import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const sourcePlacement = 'https://www.city.mito.lg.jp/page/4338.html';

const updates = [
  {
    code: '0820132',
    facilityNameJa: '内原郷土史義勇軍資料館',
    publishedAddressJa: '茨城県水戸市内原町1497-16',
    sourceAddress: 'https://www.city.mito.lg.jp/site/education/73425.html',
  },
  {
    code: '0820133',
    facilityNameJa: '稲荷第二市民センター',
    publishedAddressJa: '茨城県水戸市栗崎町1695-4',
    sourceAddress: 'https://www.city.mito.lg.jp/soshiki/144/index.html',
  },
];

for (const update of updates) {
  const station = data.stations.find(({ code }) => code === update.code);
  if (!station) throw new Error(`Station ${update.code} was not found.`);
  if (station.publishedAddressJa || station.facilityNameJa) {
    throw new Error(`Station ${update.code} already has placement metadata.`);
  }

  station.facilityNameJa = update.facilityNameJa;
  station.publishedAddressJa = update.publishedAddressJa;
  station.metadataStatus = 'Official Mito City seismic-meter placement confirmation';
  station.note = 'Mito City directly identifies the seismic-intensity meter at this facility and publishes the facility address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourcePlacement, update.sourceAddress])];
  delete station.placementLocalityJa;
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
