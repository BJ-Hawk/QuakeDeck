import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.asahi.lg.jp/uploaded/attachment/7536.pdf';
const facilitySources = [
  'https://www.city.asahi.lg.jp/soshiki/3/2773.html',
  'https://www.city.asahi.lg.jp/soshiki/7/23696.html',
];
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = new Map([
  ['1221531', ['千葉県旭市南堀之内10番地', 'ひかた市民センター（旧干潟支所）']],
  ['1221532', ['千葉県旭市高生1番地', '海上公民館（海上出張所）']],
]);

for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update) continue;
  if (station.publishedAddressJa !== null || station.facilityNameJa !== null || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Refusing unexpected pre-update state for ${station.code}`);
  }
  station.publishedAddressJa = update[0];
  station.facilityNameJa = update[1];
  station.metadataStatus = 'source_verified';
  station.sourceUrls.push(placementSource, ...facilitySources);
  station.note = 'An Asahi City seismic-survey response states that the city’s intensity meters are installed at City Hall and its branch offices. The station locality exactly matches the named branch site; current City pages publish the successor facility and its Japanese address at that same site.';
  station.placementPrecision = 'exact_address';
  updates.delete(station.code);
}

if (updates.size) throw new Error(`Missing expected stations: ${[...updates.keys()].join(', ')}`);
if (data.coverage.exactPlacementAddressUpdates !== 2134 || data.coverage.localityPlacementRecords !== 1553) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2136;
data.coverage.localityPlacementRecords = 1551;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
