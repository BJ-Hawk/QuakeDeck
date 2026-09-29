import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '4538230';
const placementSource = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const addressSource = 'https://www.town.kunitomi.miyazaki.jp/main/welcome/';
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
const station = data.stations.find((item) => item.code === code);
if (!station || station.nameJa !== '国富町本庄' || station.prefectureJa !== '宮崎県') throw new Error(`Unexpected station identity for ${code}`);
if (station.facilityNameJa !== null || station.publishedAddressJa !== null || station.placementPrecision !== 'municipality_or_ward') throw new Error(`Unexpected starting placement fields for ${code}`);

const replacement = {
  ...station,
  facilityNameJa: '国富町役場',
  facilityNameEn: 'Kunitomi Town Hall',
  publishedAddressJa: '宮崎県東諸県郡国富町大字本庄4800',
  metadataStatus: 'Official seismic-network facility placement',
  sourceUrls: [...new Set([...station.sourceUrls, placementSource, addressSource])],
  note: 'The Miyazaki seismic-network catalogue places the station on Kunitomi Town Hall grounds at 本庄4800. Kunitomi Town confirms that its current town hall remains at the same address.',
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
