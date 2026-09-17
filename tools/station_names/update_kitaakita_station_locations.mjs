import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.city.kitaakita.akita.jp/uploads/public/archive_0000005708_00/007_siryouhenn_4.pdf';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = new Map([
  ['0521333', ['秋田県北秋田市米内沢字七曲23', '森吉支所庁舎']],
  ['0521334', ['秋田県北秋田市新田目字大野82-2', '合川支所庁舎']],
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
  station.sourceUrls.push(source);
  station.note = 'Kitaakita City’s disaster-plan appendix directly lists this prefectural intensity meter at the named branch-office building and Japanese address.';
  station.placementPrecision = 'exact_address';
  updates.delete(station.code);
}

if (updates.size) throw new Error(`Missing expected stations: ${[...updates.keys()].join(', ')}`);
if (data.coverage.exactPlacementAddressUpdates !== 2139 || data.coverage.localityPlacementRecords !== 1548) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2141;
data.coverage.localityPlacementRecords = 1546;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
