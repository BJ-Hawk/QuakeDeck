import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20250214065336_0_Z__J_JPSP_20250214065200_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = new Map([
  ['3252835', { address: '隠岐郡隠岐の島町下西78-2', facility: '隠岐の島町役場' }],
  ['4520137', { address: '宮崎市清武町西新町1-1', facility: '清武町総合支所' }],
]);

for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update) continue;
  if (station.publishedAddressJa !== null || station.facilityNameJa !== null || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Refusing unexpected pre-update state for ${station.code}`);
  }
  station.publishedAddressJa = update.address;
  station.facilityNameJa = update.facility;
  station.metadataStatus = 'Official JMA March 2025 station-code notice and published address';
  station.sourceUrls.push(source);
  station.note = 'JMA’s March 2025 current station-code notice directly pairs this station name with the published host facility and Japanese address.';
  station.placementPrecision = 'exact_address';
  updates.delete(station.code);
}
if (updates.size) throw new Error(`Missing expected stations: ${[...updates.keys()].join(', ')}`);
if (data.coverage.exactPlacementAddressUpdates !== 2024 || data.coverage.localityPlacementRecords !== 1663) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2026;
data.coverage.localityPlacementRecords = 1661;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
