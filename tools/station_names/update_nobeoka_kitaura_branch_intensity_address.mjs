import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '4520334';
const placementSource = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const addressSource = 'https://www.city.nobeoka.miyazaki.jp/soshiki/85/';
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
const station = data.stations.find((item) => item.code === code);
if (!station || station.nameJa !== '延岡市北浦町古江' || station.prefectureJa !== '宮崎県') throw new Error(`Unexpected station identity for ${code}`);
if (station.facilityNameJa !== null || station.publishedAddressJa !== null || station.placementPrecision !== 'municipality_or_ward') throw new Error(`Unexpected starting placement fields for ${code}`);

const replacement = {
  ...station,
  facilityNameJa: '延岡市役所北浦総合支所',
  facilityNameEn: 'Nobeoka City Hall, Kitaura General Branch',
  publishedAddressJa: '宮崎県延岡市北浦町古江1930',
  metadataStatus: 'Official seismic-network facility placement',
  sourceUrls: [...new Set([...station.sourceUrls, placementSource, addressSource])],
  note: 'The Miyazaki seismic-network catalogue places the station on the former Kitaura Town Hall grounds at 古江1930. Nobeoka City confirms that the current Kitaura General Branch remains at the same address.',
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
