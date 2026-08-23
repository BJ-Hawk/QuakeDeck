import json, os, re, unicodedata
from pathlib import Path
import pdfplumber

PATH=Path('outputs/station-name-audit/station_metadata_sources.json')
PDF=Path(os.environ['TEMP'])/'quakedeck-miyagi-current.pdf'
URL='https://www.fdma.go.jp/bousaikeikaku/hokkaido_tohoku/miyagi/items/05_miyagi_shiryou.pdf'
prov={'県':'地方公共団体','防科研':'防災科学技術研究所','気象庁':'気象庁'}
norm=lambda x:unicodedata.normalize('NFKC',x or '').replace(' ','').replace('　','').strip()
d=json.loads(PATH.read_text(encoding='utf8')); stations=d['stations']; updates=[]
with pdfplumber.open(PDF) as pdf:
  rows=pdf.pages[108].extract_tables()[0][2:]+pdf.pages[109].extract_tables()[0][2:]
for row in rows:
  name=norm(row[5]); combined=norm(row[9]); m=re.match(r'^(県|防科研|気象庁)(.*)$',combined)
  if not name or not m: continue
  candidates=[s for s in stations if s['prefectureJa']=='宮城県' and s.get('providerJa')==prov[m.group(1)] and norm(s.get('nameJa'))==name]
  if len(candidates)!=1 or candidates[0].get('publishedAddressJa'): continue
  updates.append((candidates[0],m.group(2),norm(row[10])))
for s,address,facility in updates:
  s['publishedAddressJa']=address;s['facilityNameJa']=facility;s['placementPrecision']='exact_address';s.pop('placementLocalityJa',None);s['sourceUrls']=[*dict.fromkeys([*(s.get('sourceUrls') or []),URL])];s['metadataStatus']='source_verified';s['note']='Verified against the Fire and Disaster Management Agency’s Miyagi station table.'
d['coverage']['publishedAddresses']+=len(updates);d['coverage']['exactPlacementAddressUpdates']+=len(updates);d['coverage']['localityPlacementRecords']-=len(updates)
PATH.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print('updated',len(updates),[s['code'] for s,_,_ in updates])
