import json, os, re, unicodedata
from pathlib import Path
import pdfplumber

data_path = Path('outputs/station-name-audit/station_metadata_sources.json')
pdf_path = Path(os.environ['TEMP']) / 'quakedeck-aichi-stations.pdf'
source = 'https://www.pref.aichi.jp/uploaded/attachment/593301.pdf'

def norm(value):
    value = unicodedata.normalize('NFKC', value or '')
    value = re.sub(r'[\s　\-－‐]', '', value)
    return value.replace('大字', '').replace('字', '')

data = json.loads(data_path.read_text(encoding='utf-8'))
rows = []
with pdfplumber.open(pdf_path) as pdf:
    for page_index in (29, 30):
        for table in pdf.pages[page_index].extract_tables():
            for row in table:
                if len(row) != 4 or row[0] == '市町村名' or not row[3]:
                    continue
                # The final table on page 31 covers display-only equipment, not meters.
                if row[1] == '表示装置':
                    continue
                rows.append((row[2], row[3]))

updates = []
for facility, address in rows:
    address_key = norm(address)
    candidates = []
    for station in data['stations']:
        if station.get('prefectureJa') != '愛知県' or station.get('publishedAddressJa'):
            continue
        station_key = norm(station['nameJa']).removeprefix('愛知')
        if len(station_key) >= 5 and station_key in address_key:
            candidates.append(station)
    if len(candidates) == 1:
        updates.append((candidates[0], facility, address))

for station, facility, address in updates:
    station['facilityNameJa'] = facility
    station['publishedAddressJa'] = address
    station['placementPrecision'] = 'exact_address'
    station.pop('placementLocalityJa', None)
    station['sourceUrls'] = list(dict.fromkeys([*(station.get('sourceUrls') or []), source]))
    station['metadataStatus'] = 'source_verified'
    station['note'] = 'Verified against Aichi Prefecture’s 2025 seismic-intensity network station table.'

data['coverage']['publishedAddresses'] += len(updates)
data['coverage']['exactPlacementAddressUpdates'] += len(updates)
data['coverage']['localityPlacementRecords'] -= len(updates)
data_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('updated', len(updates))
for station, facility, address in updates:
    print(station['code'], station['nameJa'], '=>', facility, '|', address)
