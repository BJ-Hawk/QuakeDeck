import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const placementSource = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const addressSource = 'https://www.city.miyazaki.miyazaki.jp/city/management/contact/328.html';
const updates = [
  {
    code: '4520135',
    nameJa: '宮崎市高岡町内山',
    facilityNameJa: '宮崎市高岡総合支所',
    facilityNameEn: 'Miyazaki City, Takaoka General Branch',
    publishedAddressJa: '宮崎県宮崎市高岡町内山2887',
    note: 'The Miyazaki seismic-network catalogue places the station on the former Takaoka Town Hall grounds at 内山2887. Miyazaki City confirms that the current Takaoka General Branch remains at the same address.',
  },
  {
    code: '4520138',
    nameJa: '宮崎市佐土原町下田島',
    facilityNameJa: '宮崎市佐土原総合支所',
    facilityNameEn: 'Miyazaki City, Sadowara General Branch',
    publishedAddressJa: '宮崎県宮崎市佐土原町下田島20660',
    note: 'The Miyazaki seismic-network catalogue places the station on the former Sadowara Town Hall grounds at 下田島20660. Miyazaki City confirms that the current Sadowara General Branch remains at the same address.',
  },
];
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
let output = raw;
for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa || station.prefectureJa !== '宮崎県') throw new Error(`Unexpected station identity for ${update.code}`);
  if (station.facilityNameJa !== null || station.publishedAddressJa !== null || station.placementPrecision !== 'municipality_or_ward') throw new Error(`Unexpected starting placement fields for ${update.code}`);
  const replacement = {
    ...station,
    facilityNameJa: update.facilityNameJa,
    facilityNameEn: update.facilityNameEn,
    publishedAddressJa: update.publishedAddressJa,
    metadataStatus: 'Official seismic-network facility placement',
    sourceUrls: [...new Set([...station.sourceUrls, placementSource, addressSource])],
    note: update.note,
    placementPrecision: 'exact_address',
  };
  const marker = `"code": "${update.code}"`;
  const codeIndex = output.indexOf(marker);
  if (codeIndex < 0 || output.indexOf(marker, codeIndex + 1) >= 0) throw new Error(`Could not uniquely locate ${update.code}`);
  const startIndex = output.lastIndexOf('    {', codeIndex);
  const endIndex = output.indexOf('\n    }', codeIndex);
  if (startIndex < 0 || endIndex < 0) throw new Error(`Could not find ${update.code} record boundaries`);
  const newline = output.includes('\r\n') ? '\r\n' : '\n';
  output = `${output.slice(0, startIndex)}${JSON.stringify(replacement, null, 2).replaceAll('\n', newline)}${output.slice(endIndex + '\n    }'.length)}`;
}
const verified = JSON.parse(output).stations;
for (const update of updates) {
  const station = verified.find((item) => item.code === update.code);
  if (!station || station.facilityNameJa !== update.facilityNameJa || station.publishedAddressJa !== update.publishedAddressJa || station.placementPrecision !== 'exact_address') throw new Error(`Post-write validation failed for ${update.code}`);
}
if (apply) writeFileSync(inputPath, output);
console.log(`${apply ? 'Updated' : 'Validated'} ${updates.map((update) => update.code).join(', ')}`);
