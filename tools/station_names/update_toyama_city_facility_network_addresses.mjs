import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  {
    code: '1620133',
    facility: '婦中消防署',
    address: '富山県富山市婦中町笹倉128',
    sourceUrl: 'https://www.city.toyama.lg.jp/kurashi/1011960/1010683/1010691/1007797.html',
  },
  {
    code: '1620136',
    facility: '大山消防署',
    address: '富山県富山市花崎737',
    sourceUrl: 'https://www.city.toyama.lg.jp/kurashi/1011960/1010683/1010686/1007774.html',
  },
  {
    code: '1620137',
    facility: '大沢野消防署',
    address: '富山県富山市上二杉202',
    sourceUrl: 'https://www.city.toyama.lg.jp/kurashi/1011960/1010683/1010688/1016782/1007829.html',
  },
  {
    code: '1620139',
    facility: '細入総合行政センター',
    address: '富山県富山市楡原1088',
    sourceUrl: 'https://www.city.toyama.lg.jp/kurashi/sumai/1010267/1008055.html',
  },
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const { code, facility, address, sourceUrl } of updates) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station) throw new Error(`Station ${code} not found.`);
  if (station.facilityNameJa !== facility) {
    throw new Error(`Station ${code} does not identify ${facility} as its facility.`);
  }
  if (station.publishedAddressJa) throw new Error(`Station ${code} already has an address.`);

  station.publishedAddressJa = address;
  station.metadataStatus = 'Official prefectural placement and municipal address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; the municipality publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updates.length} stations.`);
