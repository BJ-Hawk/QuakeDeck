import { readFileSync, writeFileSync } from 'node:fs';
const dataPath = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.city.shimonoseki.lg.jp/uploaded/attachment/85337.pdf';
const rows = [
 ['3520120','下関市役所清末支所'], ['3520138','下関市立角島診療所'],
 ['3520143','下関市役所豊田総合支所'], ['3520144','下関市役所'], ['3520145','下関市役所菊川総合支所']
];
const data = JSON.parse(readFileSync(dataPath, 'utf8')); const byCode = new Map(data.stations.map(s => [s.code, s]));
for (const [code, facility] of rows) { const s = byCode.get(code); if (!s || s.prefectureJa !== '山口県' || s.facilityNameJa) throw new Error(`Refusing ${code}`); s.facilityNameJa=facility; s.sourceUrls=[...new Set([...(s.sourceUrls||[]),source])]; s.metadataStatus='source_verified'; s.note='Verified against Shimonoseki City’s current seismic-observation facility list.'; }
writeFileSync(dataPath, `${JSON.stringify(data,null,2)}\n`, 'utf8'); console.log(`updated ${rows.length}`);
