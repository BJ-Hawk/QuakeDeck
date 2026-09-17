import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = fs.readFileSync(path);
const startMarker = Buffer.from('"code": "2120342"', 'utf8');
const start = source.indexOf(startMarker);
if (start < 0) throw new Error('Station 2120342 was not found.');

const endMarker = Buffer.from('\n    },', 'utf8');
const end = source.indexOf(endMarker, start);
if (end < 0) throw new Error('Station 2120342 record terminator was not found.');

const record = source.subarray(start, end).toString('utf8');
if (!record.includes('"metadataStatus": "Official observation-locality verification"')) {
  throw new Error('Station 2120342 does not have the expected verification status.');
}
if (!record.includes('"placementLocalityJa": "高根町上ケ洞"')) {
  throw new Error('Station 2120342 does not have the expected verified locality.');
}

const officialUrl = 'https://www.data.jma.go.jp/eqev/data/gaikyo/monthly/202402/202402furoku_1.pdf';
if (record.includes(officialUrl)) throw new Error('The official JMA source URL is already present.');

const updated = record.replace(
  /("https:\/\/catalog\.registries\.digital\.go\.jp\/rc\/dataset\/ba-o1-000000_g2-000003")\n(\s*)\],/,
  (_match, catalogueUrl, indent) => `${catalogueUrl},\n${indent}"${officialUrl}"\n${indent}],`,
);
if (updated === record) throw new Error('The source URL insertion did not match the current record formatting.');

fs.writeFileSync(path, Buffer.concat([source.subarray(0, start), Buffer.from(updated, 'utf8'), source.subarray(end)]));
