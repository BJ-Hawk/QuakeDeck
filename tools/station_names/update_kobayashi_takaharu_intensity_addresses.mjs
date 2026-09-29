import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const placementSource = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const updates = [
  {
    code: '4520533',
    nameJa: '小林市中原',
    facilityNameJa: '小林市須木庁舎',
    facilityNameEn: 'Kobayashi City, Suki Office',
    publishedAddressJa: '宮崎県小林市須木中原1757',
    addressSource: 'https://www.city.kobayashi.lg.jp/material/files/group/11/1601051423152021122308500611f_2.pdf',
    note: 'The Miyazaki seismic-network catalogue places the station on the former Suki Village Hall grounds at 中原1757. Kobayashi City’s official access map confirms that the current Suki Office remains at the same address.',
  },
  {
    code: '4520534',
    nameJa: '小林市野尻町東麓',
    facilityNameJa: '小林市野尻庁舎',
    facilityNameEn: 'Kobayashi City, Nojiri Office',
    publishedAddressJa: '宮崎県小林市野尻町東麓1183-2',
    addressSource: 'https://www.city.kobayashi.lg.jp/gyoseijoho/yakusho_madoguchiannai/3274.html',
    note: 'The Miyazaki seismic-network catalogue places the station on the former Nojiri Town Hall grounds at 東麓1183-2. Kobayashi City confirms that the current Nojiri Office remains at the same address.',
  },
  {
    code: '4536130',
    nameJa: '高原町西麓',
    facilityNameJa: '高原町役場',
    facilityNameEn: 'Takaharu Town Hall',
    publishedAddressJa: '宮崎県西諸県郡高原町大字西麓899',
    addressSource: 'https://www.town.takaharu.lg.jp/map/2335.html',
    note: 'The Miyazaki seismic-network catalogue places the station on Takaharu Town Hall grounds at 西麓899. Takaharu Town confirms that its current town hall remains at the same address.',
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
    sourceUrls: [...new Set([...station.sourceUrls, placementSource, update.addressSource])],
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
