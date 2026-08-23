import json, os, re, unicodedata
from pathlib import Path
import pdfplumber

data_path = Path('outputs/station-name-audit/station_metadata_sources.json')
pdf_path = Path(os.environ['TEMP']) / 'quakedeck-nied-knet-stations.pdf'
source = 'https://www.bosai.go.jp/information/tender/supply/pdf/shiyousho.pdf'

def clean(value):
    return unicodedata.normalize('NFKC', value or '').replace('\n', '').replace(' ', '').replace('　', '')

entries = {}
with pdfplumber.open(pdf_path) as pdf:
    for page in pdf.pages:
        for table in page.extract_tables():
            for row in table:
                if len(row) < 6:
                    continue
                code = clean(row[1])
                if not re.fullmatch(r'[A-Z]{3}[0-9]{3}', code):
                    continue
                prefecture, address, facility = clean(row[3]), clean(row[4]), clean(row[5])
                if not (prefecture.endswith('県') or prefecture.endswith('府') or prefecture.endswith('都') or prefecture == '北海道'):
                    continue
                if not address:
                    continue
                if code in entries and entries[code] != (prefecture + address, facility):
                    raise RuntimeError(f'conflicting source rows for {code}')
                entries[code] = (prefecture + address, facility)

data = json.loads(data_path.read_text(encoding='utf-8'))
updates = []
for station in data['stations']:
    code = station.get('providerStationCode')
    if (station.get('providerStationNetwork') != 'K-NET' or station.get('providerJa') != '防災科学技術研究所'
            or station.get('publishedAddressJa') or code not in entries):
        continue
    address, facility = entries[code]
    updates.append((station, address, facility))

for station, address, facility in updates:
    station['publishedAddressJa'] = address
    if facility:
        station['facilityNameJa'] = facility
    station['placementPrecision'] = 'exact_address'
    station.pop('placementLocalityJa', None)
    station['sourceUrls'] = list(dict.fromkeys([*(station.get('sourceUrls') or []), source]))
    station['metadataStatus'] = 'source_verified'
    station['note'] = 'Verified against NIED’s official K-NET station specification.'

data['coverage']['publishedAddresses'] += len(updates)
data['coverage']['exactPlacementAddressUpdates'] += len(updates)
data['coverage']['localityPlacementRecords'] -= len(updates)
data_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('source entries', len(entries), 'updated', len(updates))
for station, address, facility in updates:
    print(station['code'], station['providerStationCode'], '=>', address, '|', facility)
