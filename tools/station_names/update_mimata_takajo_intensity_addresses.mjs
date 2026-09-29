import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const placementSource = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const updates = [
  {
    code: '4534131',
    nameJa: '三股町五本松',
    facilityNameJa: '三股町役場',
    facilityNameEn: 'Mimata Town Hall',
    publishedAddressJa: '宮崎県北諸県郡三股町五本松1-1',
    addressSource: 'https://www.town.mimata.lg.jp/contents/456.html',
    note: 'The Miyazaki seismic-network catalogue places the station on Mimata Town Hall grounds at 五本松1-1. Mimata Town confirms that its current town hall remains at the same address.',
  },
  {
    code: '4520236',
    nameJa: '都城市高城町穂満坊',
    facilityNameJa: '都城市高城総合支所',
    facilityNameEn: 'Miyakonojo City, Takajo General Branch',
    publishedAddressJa: '宮崎県都城市高城町穂満坊306',
    addressSource: 'https://www.city.miyakonojo.miyazaki.jp/soshiki/23/53422.html',
    note: 'The Miyazaki seismic-network catalogue places the station on the former Takajo Town Hall grounds at 穂満坊306. Miyakonojo City confirms that the current Takajo General Branch remains at the same address.',
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
