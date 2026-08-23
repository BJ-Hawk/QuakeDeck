import { readFileSync, writeFileSync } from 'node:fs';
const path='outputs/station-name-audit/station_metadata_sources.json';
const source='https://www.city.akita.akita.jp/city/pl/mn/gappei/kyougikai/naiyou/giann34kannkei.pdf';
const rows=[['0520120','秋田市八橋運動公園'],['0520131','河辺町庁舎裏（中央保育園側）'],['0520134','雄和町庁舎裏側']];
const d=JSON.parse(readFileSync(path,'utf8')), by=new Map(d.stations.map(s=>[s.code,s]));
for(const [code,facilityNameJa] of rows){const s=by.get(code);if(!s||s.prefectureJa!=='秋田県'||s.facilityNameJa)throw Error(code);s.facilityNameJa=facilityNameJa;s.sourceUrls=[...new Set([...(s.sourceUrls||[]),source])];s.metadataStatus='source_verified';s.note='Verified against Akita City’s seismic-intensity network placement record.';}
writeFileSync(path,`${JSON.stringify(d,null,2)}\n`);console.log(`updated ${rows.length}`);
