import { readFileSync, writeFileSync } from 'node:fs';
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.kamikawa.pref.hokkaido.lg.jp/fs/8/2/6/5/3/9/9/_/03_%E4%B8%8A%E5%B7%9D%E7%B7%8F%E5%90%88%E6%8C%AF%E8%88%88%E5%B1%80%E5%9C%B0%E5%9F%9F%E7%81%BD%E5%AE%B3%E5%AF%BE%E7%AD%96%E8%A6%81%E7%B6%B1(%E8%B3%87%E6%96%99%E7%B7%A8)%E3%80%90R5.4%E6%94%B9%E6%AD%A3%E3%80%91.pdf';
const facilities = {'0145231':'鷹栖町役場','0145332':'東神楽町役場','0145431':'当麻町役場','0145531':'比布町役場','0145830':'東川町役場','0146531':'剣淵町役場'};
const data = JSON.parse(readFileSync(path, 'utf8'));
for (const [code, facilityNameJa] of Object.entries(facilities)) { const s=data.stations.find(x=>x.code===code); if(!s)throw Error(code); s.facilityNameJa=facilityNameJa; s.sourceUrls=[...new Set([...(s.sourceUrls??[]),source])]; }
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
