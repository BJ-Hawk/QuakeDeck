import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '4520436';
const placementSource = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const addressSource = 'https://www.pref.miyazaki.lg.jp/shouhi/about/nichinan.html';
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
const station = data.stations.find((item) => item.code === code);
if (!station || station.nameJa !== '日南市中央通' || station.prefectureJa !== '宮崎県') throw new Error(`Unexpected station identity for ${code}`);
if (station.facilityNameJa !== null || station.publishedAddressJa !== null || station.placementPrecision !== 'municipality_or_ward') throw new Error(`Unexpected starting placement fields for ${code}`);

const replacement = {
  ...station,
  facilityNameJa: '日南市役所',
  facilityNameEn: 'Nichinan City Hall',
  publishedAddressJa: '宮崎県日南市中央通1-1-1',
  metadataStatus: 'Official seismic-network facility placement',
  sourceUrls: [...new Set([...station.sourceUrls, placementSource, addressSource])],
  note: 'The Miyazaki seismic-network catalogue places the station on Nichinan City Hall grounds in 中央通1丁目. Miyazaki Prefecture identifies the current Nichinan City Hall main building at 中央通1-1-1.',
  placementPrecision: 'exact_address',
};
const marker = `"code": "${code}"`;
const codeIndex = raw.indexOf(marker);
if (codeIndex < 0 || raw.indexOf(marker, codeIndex + 1) >= 0) throw new Error(`Could not uniquely locate ${code}`);
const startIndex = raw.lastIndexOf('    {', codeIndex);
const endIndex = raw.indexOf('\n    }', codeIndex);
if (startIndex < 0 || endIndex < 0) throw new Error(`Could not find ${code} record boundaries`);
const newline = raw.includes('\r\n') ? '\r\n' : '\n';
const output = `${raw.slice(0, startIndex)}${JSON.stringify(replacement, null, 2).replaceAll('\n', newline)}${raw.slice(endIndex + '\n    }'.length)}`;
const verified = JSON.parse(output).stations.find((item) => item.code === code);
if (!verified || verified.facilityNameJa !== replacement.facilityNameJa || verified.publishedAddressJa !== replacement.publishedAddressJa || verified.placementPrecision !== 'exact_address') throw new Error(`Post-write validation failed for ${code}`);
if (apply) writeFileSync(inputPath, output);
console.log(`${apply ? 'Updated' : 'Validated'} ${code}: ${replacement.facilityNameEn}`);
