import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  {
    code: '2043232',
    facility: '開田支所',
    address: '長野県木曽郡木曽町開田高原西野623番地1',
    sourceUrl: 'https://www.town-kiso.com/chousei/senkyo/100394/101890/',
  },
  {
    code: '2043233',
    facility: '三岳支所',
    address: '長野県木曽郡木曽町三岳6311番地',
    sourceUrl: 'https://www.town-kiso.com/chousei/senkyo/100394/101890/',
  },
  {
    code: '2043236',
    facility: '木曽福島保健センター',
    address: '長野県木曽郡木曽町福島5764番地5',
    sourceUrl: 'https://www.town-kiso.com/kodomo/kosodate/100325/101510/',
  },
];

for (const update of updates) {
  const station = data.stations.find((candidate) => candidate.code === update.code);
  if (!station) throw new Error(`Missing station ${update.code}`);
  if (station.facilityNameJa !== update.facility) {
    throw new Error(`${update.code}: expected ${update.facility}, found ${station.facilityNameJa}`);
  }
  if (station.publishedAddressJa) throw new Error(`${update.code}: address already present`);

  station.publishedAddressJa = update.address;
  station.metadataStatus = 'Official prefectural placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${update.facility} as the host site; Kiso Town publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), update.sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
