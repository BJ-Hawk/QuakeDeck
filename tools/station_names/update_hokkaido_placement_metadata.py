import json, os, re, unicodedata
from pathlib import Path
import pdfplumber
P=Path('outputs/station-name-audit/station_metadata_sources.json'); PDF=Path(os.environ['TEMP'])/'quakedeck-hokkaido-stations.pdf'; U='https://www.pref.hokkaido.lg.jp/fs/1/3/1/3/5/6/7/2/_/%E2%97%8B%E3%80%90R7%E3%80%91%E8%B3%87%E6%96%992-1%28%E8%A4%87%E6%95%B0%E3%82%B7%E3%83%BC%E3%83%88%29.pdf'
def n(s): return unicodedata.normalize('NFKC',s or '').replace(' ','').replace('　','').replace('ヶ','ケ')
d=json.loads(P.read_text(encoding='utf8')); upd=[]
with pdfplumber.open(PDF) as pdf:
 rows=[]
 for i in range(13,17): rows+=pdf.pages[i].extract_tables()[0][2:]
for r in rows:
 name=n(r[1]); address=unicodedata.normalize('NFKC',r[2] or '').strip(); candidates=[s for s in d['stations'] if s['prefectureJa']=='北海道' and n(s['nameJa'])==name and not s.get('publishedAddressJa')]
 if len(candidates)!=1 or not address: continue
 facility=(re.search(r'（(.+?)）',address) or [None,None])[1]; upd.append((candidates[0],address,facility))
for s,a,f in upd:
 s['publishedAddressJa']=a;s['facilityNameJa']=f;s['placementPrecision']='exact_address';s.pop('placementLocalityJa',None);s['sourceUrls']=[*dict.fromkeys([*(s.get('sourceUrls') or []),U])];s['metadataStatus']='source_verified';s['note']='Verified against Hokkaido’s current official seismic-observation station table.'
d['coverage']['publishedAddresses']+=len(upd);d['coverage']['exactPlacementAddressUpdates']+=len(upd);d['coverage']['localityPlacementRecords']-=len(upd);P.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print('updated',len(upd),[s['code'] for s,_,_ in upd])
