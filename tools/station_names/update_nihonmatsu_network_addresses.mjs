import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const sourceUrl = 'https://www.city.nihonmatsu.lg.jp/data/doc/1655723818_doc_151_0.pdf';
const updates = [
  ['0721030', '二本松市金色', '二本松市役所本庁', '福島県二本松市金色403番地1', 'Nihonmatsu City’s disaster plan places the measuring-intensity meter at the City Hall main-office premises and publishes its exact address.'],
  ['0721031', '二本松市油井', '二本松市役所安達支所', '福島県二本松市油井字濡石1番地2', 'Nihonmatsu City’s disaster plan places the measuring-intensity meter at the Adachi Branch premises and publishes its exact address.'],
  ['0721032', '二本松市小浜', '二本松市役所岩代支所', '福島県二本松市小浜字北月山27番地', 'Nihonmatsu City’s disaster plan places the measuring-intensity meter at the Iwadate Branch premises and publishes its exact address.'],
  ['0721034', '二本松市針道', '二本松市役所東和支所', '福島県二本松市針道字蔵下22番地', 'Nihonmatsu City’s disaster plan places the measuring-intensity meter at the Towa Branch premises and publishes its exact address.']
];

for (const [code, nameJa, facilityNameJa, publishedAddressJa, note] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.nameJa !== nameJa) throw new Error(`${code}: unexpected station label ${station.nameJa}`);
  if (station.facilityNameJa || station.publishedAddressJa) throw new Error(`${code}: placement already present`);
  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.metadataStatus = 'Official municipal seismic-network placement and address';
  station.note = note;
  station.placementPrecision = 'exact_address';
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
