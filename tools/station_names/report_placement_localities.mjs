import { readFileSync, writeFileSync } from 'node:fs';

const catalog = JSON.parse(readFileSync('outputs/station-name-audit/station_metadata_sources.json', 'utf8'));

function municipalityFromAddress(address) {
  const city = address.match(/^(.+?市(?:.+?区)?)/)?.[1];
  if (city) return city;
  const countyTown = address.match(/^(.+?郡.+?[町村])/ )?.[1];
  if (countyTown) return countyTown;
  return address.match(/^(.+?[町村])/ )?.[1] ?? null;
}

function municipalityFromStationName(name) {
  const city = name.match(/^(.+?市(?:.+?区)?)/)?.[1];
  if (city) return city;
  const countyTown = name.match(/^(.+?郡.+?[町村])/ )?.[1];
  if (countyTown) return countyTown;
  const designatedCity = name.match(/^(札幌|仙台|さいたま|千葉|東京|横浜|川崎|相模原|新潟|静岡|浜松|名古屋|京都|大阪|堺|神戸|岡山|広島|北九州|福岡|熊本)(.+?区)/)?.slice(1);
  if (designatedCity) return designatedCity[0] === '東京' ? `東京都${designatedCity[1]}` : `${designatedCity[0]}市${designatedCity[1]}`;
  return name.match(/^(.+?[町村])/ )?.[1] ?? null;
}

const localitiesByMunicipality = new Map();
for (const station of catalog.stations.filter((station) => station.publishedAddressJa)) {
  const locality = municipalityFromAddress(station.publishedAddressJa);
  if (!locality) continue;
  if (!localitiesByMunicipality.has(station.municipalityCode)) localitiesByMunicipality.set(station.municipalityCode, new Set());
  localitiesByMunicipality.get(station.municipalityCode).add(locality);
}

const results = catalog.stations.filter((station) => !station.publishedAddressJa).map((station) => {
  const known = [...(localitiesByMunicipality.get(station.municipalityCode) ?? [])];
  const locality = known.length === 1 ? known[0] : municipalityFromStationName(station.nameJa);
  return {
    code: station.code,
    municipalityCode: station.municipalityCode,
    stationNameJa: station.nameJa,
    locality,
    method: known.length === 1 ? 'same-municipality source-backed address' : locality ? 'station name administrative prefix' : null,
  };
});

if (process.argv.includes('--apply')) {
  for (const station of catalog.stations) {
    if (station.publishedAddressJa) {
      station.placementPrecision = 'exact_address';
      continue;
    }
    const result = results.find((candidate) => candidate.code === station.code);
    station.placementLocalityJa = result.locality ?? station.prefectureJa;
    station.placementPrecision = result.locality ? 'municipality_or_ward' : 'prefecture';
  }
  catalog.coverage = {
    ...catalog.coverage,
    localityPlacementRecords: results.length,
    prefectureOnlyPlacementRecords: results.filter((result) => !result.locality).length,
  };
  writeFileSync('outputs/station-name-audit/station_metadata_sources.json', `${JSON.stringify(catalog, null, 2)}\n`);
}

console.log(JSON.stringify({
  exactAddressStations: catalog.stations.length - results.length,
  localityCandidates: results.filter((result) => result.locality).length,
  unresolvedAtMunicipalityPrecision: results.filter((result) => !result.locality).length,
  sampleUnresolved: results.filter((result) => !result.locality).slice(0, 30),
}, null, 2));
