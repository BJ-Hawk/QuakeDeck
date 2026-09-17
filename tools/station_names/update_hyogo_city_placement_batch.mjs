import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const takarazukaSourceUrl = 'https://www.city.takarazuka.hyogo.jp/1013056/1011509/1013061/1012289.html';
const nishiwakiPlacementSourceUrl = 'https://www.city.nishiwaki.lg.jp/material/files/group/57/shiryou_R06_3.pdf';
const nishiwakiAddressSourceUrl = 'https://www.city.nishiwaki.lg.jp/kakukanogoannai/kurashianshinbu/bousaikankyouka/kankyougyouseitorikumi/27328.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  {
    code: '2821431',
    prefectureJa: '兵庫県',
    nameJa: '宝塚市東洋町',
    facilityNameJa: '宝塚市役所',
    publishedAddressJa: '兵庫県宝塚市東洋町1番1号',
    metadataStatus: 'Official Takarazuka City seismic-instrument placement and City Hall record',
    sourceUrls: [takarazukaSourceUrl],
    note: 'Takarazuka City states that the station reported as 宝塚市東洋町 is installed on the City Hall grounds, and publishes the City Hall address.',
  },
  {
    code: '2821331',
    prefectureJa: '兵庫県',
    nameJa: '西脇市黒田庄町前坂',
    facilityNameJa: '黒っこプラザ',
    publishedAddressJa: '兵庫県西脇市黒田庄町前坂2140',
    metadataStatus: 'Official Nishiwaki City seismic-instrument placement and facility address record',
    sourceUrls: [nishiwakiPlacementSourceUrl, nishiwakiAddressSourceUrl],
    note: 'Nishiwaki City explicitly maps this station name to the south side of Blackko Plaza’s main entrance and publishes the facility address.',
  },
];

for (const update of updates) {
  const station = data.stations.find((entry) => entry.code === update.code);
  if (!station || station.prefectureJa !== update.prefectureJa || station.nameJa !== update.nameJa) {
    throw new Error(`Unexpected station for ${update.code}`);
  }
  if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
    throw new Error(`Station ${update.code} already has placement metadata`);
  }
  station.facilityNameJa = update.facilityNameJa;
  station.publishedAddressJa = update.publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = update.metadataStatus;
  station.sourceUrls = [...new Set([...station.sourceUrls, ...update.sourceUrls])];
  station.note = update.note;
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.map(({ code }) => code).join(', ')}.`);
