import fs from "node:fs/promises";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..", "..");
const sourcePath = path.join(root, "outputs", "station-name-audit", "station_metadata_sources.json");
const outputDir = path.join(root, "app", "src", "main", "res", "raw");

const source = JSON.parse(await fs.readFile(sourcePath, "utf8"));
const stations = source.stations;
if (!Array.isArray(stations) || stations.length !== 4360) {
  throw new Error(`Expected 4,360 station source records; found ${stations?.length ?? 0}`);
}

function buildMap(field, requireName) {
  const names = {};
  for (const station of stations) {
    const code = String(station.code ?? "");
    const name = String(station[field] ?? "").trim();
    if (!/^\d{7}$/.test(code) || (requireName && !name)) {
      throw new Error(`Invalid ${field} entry for station ${code || "(missing code)"}`);
    }
    if (names[code]) throw new Error(`Duplicate station code ${code}`);
    names[code] = name;
  }
  return names;
}

// The baseline intentionally preserves current blank values as blanks. It is a
// verbatim in-app snapshot of the previous runtime output, not a replacement.
const baseline = buildMap("automaticEnglishName", false);
const active = buildMap("resolvedEnglishName", true);
await fs.writeFile(
  path.join(root, "outputs", "station-name-audit", "station_english_names_baseline.json"),
  `${JSON.stringify({ version: 1, names: baseline }, null, 2)}\n`,
  "utf8",
);
await fs.writeFile(
  path.join(outputDir, "station_english_names.json"),
  `${JSON.stringify({ version: 1, names: active }, null, 2)}\n`,
  "utf8",
);

console.log(JSON.stringify({ baseline: Object.keys(baseline).length, active: Object.keys(active).length }));
