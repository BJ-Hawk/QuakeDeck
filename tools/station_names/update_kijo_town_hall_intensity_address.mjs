import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '4540431';
const placementSource = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const addressSource = 'https://www.town.kijo.lg.jp/sosikikarasagasu/soumuzaiseika/1/2/747.html';
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
const station = data.stations.find((item) => item.code === code);

if (!station || station.nameJa !== '木城町高城' || station.prefectureJa !== '宮崎県') {
  throw new Error(`Unexpected station identity for ${code}`);
}
if (station.facilityNameJa !== null || station.publishedAddressJa !== null || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error(`Unexpected starting placement fields for ${code}`);
}

const expected = {
  facilityNameJa: '木城町役場',
  facilityNameEn: 'Kijo Town Hall',
  publishedAddressJa: '宮崎県児湯郡木城町大字高城1227-2',
  metadataStatus: 'Official seismic-network facility placement',
  placementPrecision: 'exact_address',
};
const replacement = {
  ...station,
  ...expected,
  sourceUrls: [...new Set([...station.sourceUrls, placementSource, addressSource])],
  note: 'The Miyazaki seismic-network catalogue places Kijo station on Kijo Town Hall grounds at 高城1227-2. The town publishes the current Town Hall address as 高城1227-1; the recorded address preserves the catalogue’s direct instrument-location parcel.',
};
const codeMarker = `"code": "${code}"`;
const codeIndex = raw.indexOf(codeMarker);
if (codeIndex < 0 || raw.indexOf(codeMarker, codeIndex + 1) >= 0) throw new Error(`Could not uniquely locate ${code} record`);
const startIndex = raw.lastIndexOf('    {', codeIndex);
const endIndex = raw.indexOf('\n    }', codeIndex);
if (startIndex < 0 || endIndex < 0) throw new Error(`Could not find ${code} record boundaries`);
const originalRecord = raw.slice(startIndex, endIndex + '\n    }'.length);
const newline = raw.includes('\r\n') ? '\r\n' : '\n';
const replacementRecord = JSON.stringify(replacement, null, 2).replaceAll('\n', newline);
if (originalRecord.split(codeMarker).length !== 2) throw new Error(`Unexpected ${code} record content`);
const output = `${raw.slice(0, startIndex)}${replacementRecord}${raw.slice(endIndex + '\n    }'.length)}`;
const verified = JSON.parse(output).stations.find((item) => item.code === code);
if (!verified || Object.entries(expected).some(([key, value]) => verified[key] !== value)) throw new Error(`Post-write validation failed for ${code}`);

if (apply) writeFileSync(inputPath, output);
console.log(`${apply ? 'Updated' : 'Validated'} ${code}: ${expected.facilityNameEn}`);
