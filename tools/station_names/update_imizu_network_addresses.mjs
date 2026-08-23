import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.city.imizu.toyama.jp/appupload/EDIT/142/142224.pdf';
const placements = new Map([
  ['1621134', ['射水市役所大島分庁舎', '富山県射水市小島703']],
  ['1621135', ['新湊消防署', '富山県射水市本町2-13-1']],
  ['1621136', ['射水市消防本部', '富山県射水市橋下条1522番地']],
  ['1621137', ['射水市消防署大門出張所', '富山県射水市二口1081']],
  ['1621138', ['下村小学校', '富山県射水市加茂中部1051']],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  const placement = placements.get(station.code);
  if (!placement) continue;
  if (station.facilityNameJa || station.publishedAddressJa) {
    throw new Error(`${station.code} already has placement data.`);
  }
  const [facilityNameJa, publishedAddressJa] = placement;
  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Imizu City publishes this station in its earthquake-observation table with the host facility and address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  updated += 1;
}

if (updated !== placements.size) throw new Error(`Expected ${placements.size} updates, made ${updated}.`);
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updated} Imizu stations.`);
