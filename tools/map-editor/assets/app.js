const PATHS = {
    placeNames: 'app/src/main/res/raw/jma_place_names.json',
    municipality: {
        geometry: 'app/src/main/res/raw/jma_quake_municipalities_topology.gz',
        fine: 'app/src/main/res/raw/jma_municipality_fine_boundaries.gz',
        warning: 'app/src/main/res/raw/jma_municipality_warning_boundaries.gz',
        prefecture: 'app/src/main/res/raw/jma_municipality_prefecture_boundaries.gz',
        overrides: 'tools/source/jma_municipality_boundary_overrides.json',
        baseline: {
            geometry: 'tools/source/map_editor_baseline/municipality/jma_quake_municipalities_topology.gz',
            fine: 'tools/source/map_editor_baseline/municipality/jma_municipality_fine_boundaries.gz',
            warning: 'tools/source/map_editor_baseline/municipality/jma_municipality_warning_boundaries.gz',
            prefecture: 'tools/source/map_editor_baseline/municipality/jma_municipality_prefecture_boundaries.gz',
            overrides: 'tools/source/map_editor_baseline/municipality/jma_municipality_boundary_overrides.json',
        },
    },
    jma: {
        geometry: 'app/src/main/res/raw/jma_quake_regions.gz',
        borders: 'app/src/main/res/raw/jma_quake_region_borders.gz',
        overrides: 'tools/source/jma_quake_region_editor_overrides.json',
        baseline: {
            geometry: 'tools/source/map_editor_baseline/jma/jma_quake_regions.gz',
            borders: 'tools/source/map_editor_baseline/jma/jma_quake_region_borders.gz',
            overrides: 'tools/source/map_editor_baseline/jma/jma_quake_region_editor_overrides.json',
        },
    },
};

const CLASS_LABELS = {
    fine: 'Municipality',
    warning: 'Warning zone',
    prefecture: 'Prefecture',
    coast: 'Coast',
    none: 'Deleted',
};

const CLASS_COLORS = {
    fine: '#8391a8',
    warning: '#ffd166',
    prefecture: '#75c2f6',
    coast: '#e8edf5',
    selected: '#ff6b6b',
    modified: '#ff6b6b',
    search: '#8bd17c',
    coastline: '#d8e2f0',
    vertex: '#c0c8d6',
    selectedVertex: '#ff9f43',
    primaryVertex: '#ffffff',
    deleted: '#ff6b6b',
    topologyError: '#ff3b30',
    topologyWarning: '#ffb020',
    areaSelected: 'rgba(123,183,255,0.26)',
    areaHover: 'rgba(139,209,124,0.18)',
};

const GRID_SIZE = 0.01;
const VERTEX_GRID_SIZE = 0.004;
const AREA_GRID_SIZE = 0.02;
const VERTEX_VISIBLE_ZOOM = 80;
const VERTEX_HIT_ZOOM = 80;
const MAX_ZOOM_MULTIPLIER = 250_000;
const MIN_ZOOM_MULTIPLIER = 0.35;
const HISTORY_LIMIT = 40;
const OCEAN_CODE = '__OCEAN__';
const CLASS_PRIORITY = { none: 0, fine: 1, warning: 2, prefecture: 3, coast: 4 };

const BASEMAP = {
    tileUrl: (z, x, y) => `https://tile.openstreetmap.org/${z}/${x}/${y}.png`,
    minZoom: 4,
    maxZoom: 19,
    tileSize: 256,
    opacity: 0.72,
    west: 118.0,
    east: 158.0,
    south: 18.0,
    north: 48.5,
};
const OCEAN_SELECTION_PADDING = 0.06;

class ByteReader {
    constructor(buffer) {
        this.view = new DataView(buffer);
        this.offset = 0;
    }
    readBytes(count) {
        const value = new Uint8Array(this.view.buffer, this.view.byteOffset + this.offset, count);
        this.offset += count;
        return value;
    }
    readString(length) {
        return new TextDecoder().decode(this.readBytes(length));
    }
    readU8() {
        const value = this.view.getUint8(this.offset);
        this.offset += 1;
        return value;
    }
    readU16() {
        const value = this.view.getUint16(this.offset, false);
        this.offset += 2;
        return value;
    }
    readU32() {
        const value = this.view.getUint32(this.offset, false);
        this.offset += 4;
        return value;
    }
    readI32() {
        const value = this.view.getInt32(this.offset, false);
        this.offset += 4;
        return value;
    }
    readVarUint() {
        let value = 0;
        let shift = 0;
        while (shift < 35) {
            const byte = this.readU8();
            value |= (byte & 0x7f) << shift;
            if ((byte & 0x80) === 0) return value >>> 0;
            shift += 7;
        }
        throw new Error('Malformed varint');
    }
    readVarInt() {
        const value = this.readVarUint();
        return (value >>> 1) ^ -(value & 1);
    }
}

class ByteWriter {
    constructor() { this.bytes = []; }
    writeBytes(array) { for (const value of array) this.bytes.push(value & 0xff); }
    writeU8(value) { this.bytes.push(value & 0xff); }
    writeU16(value) {
        this.writeU8((value >>> 8) & 0xff);
        this.writeU8(value & 0xff);
    }
    writeU32(value) {
        this.writeU8((value >>> 24) & 0xff);
        this.writeU8((value >>> 16) & 0xff);
        this.writeU8((value >>> 8) & 0xff);
        this.writeU8(value & 0xff);
    }
    writeVarUint(value) {
        let current = value >>> 0;
        while (current >= 0x80) {
            this.writeU8((current & 0x7f) | 0x80);
            current >>>= 7;
        }
        this.writeU8(current);
    }
    writeVarInt(value) {
        this.writeVarUint(((value << 1) ^ (value >> 31)) >>> 0);
    }
    writeString(value) {
        const bytes = new TextEncoder().encode(value);
        if (bytes.length > 65535) throw new Error('String exceeds binary format limit');
        this.writeU16(bytes.length);
        this.writeBytes(bytes);
    }
    toUint8Array() { return new Uint8Array(this.bytes); }
}

class MinHeap {
    constructor() { this.items = []; }
    push(item) {
        this.items.push(item);
        let index = this.items.length - 1;
        while (index > 0) {
            const parent = Math.floor((index - 1) / 2);
            if (this.items[parent].priority <= item.priority) break;
            this.items[index] = this.items[parent];
            index = parent;
        }
        this.items[index] = item;
    }
    pop() {
        if (this.items.length === 0) return null;
        const root = this.items[0];
        const last = this.items.pop();
        if (this.items.length > 0) {
            let index = 0;
            while (true) {
                let child = index * 2 + 1;
                if (child >= this.items.length) break;
                if (child + 1 < this.items.length && this.items[child + 1].priority < this.items[child].priority) child += 1;
                if (this.items[child].priority >= last.priority) break;
                this.items[index] = this.items[child];
                index = child;
            }
            this.items[index] = last;
        }
        return root;
    }
    get size() { return this.items.length; }
}

function ensureCompressionSupport() {
    if (typeof DecompressionStream === 'undefined' || typeof CompressionStream === 'undefined') {
        throw new Error('This editor requires gzip stream support. Open it in Microsoft Edge or Google Chrome.');
    }
}

async function gunzip(arrayBuffer) {
    const stream = new Response(arrayBuffer).body.pipeThrough(new DecompressionStream('gzip'));
    return await new Response(stream).arrayBuffer();
}

async function gzip(uint8Array) {
    const stream = new Response(uint8Array).body.pipeThrough(new CompressionStream('gzip'));
    return await new Response(stream).arrayBuffer();
}

async function gunzipJson(arrayBuffer) {
    const decoded = await gunzip(arrayBuffer);
    return JSON.parse(new TextDecoder().decode(decoded));
}

async function gzipJson(payload) {
    const bytes = new TextEncoder().encode(JSON.stringify(payload, null, 0));
    return await gzip(bytes);
}

async function fetchJson(url, options) {
    const response = await fetch(url, options);
    const payload = await response.json();
    if (!response.ok || payload.ok === false) throw new Error(payload.error || `Request failed: ${response.status}`);
    return payload;
}

async function fetchArrayBuffer(path) {
    const response = await fetch(`/api/read?path=${encodeURIComponent(path)}`);
    if (!response.ok) {
        let message = `Failed to read ${path}`;
        try { message = (await response.json()).error || message; } catch { /* ignore */ }
        throw new Error(message);
    }
    return await response.arrayBuffer();
}

async function fetchOptionalArrayBuffer(path) {
    const response = await fetch(`/api/read?path=${encodeURIComponent(path)}`);
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Failed to read ${path}`);
    return await response.arrayBuffer();
}

async function fetchOptionalText(path) {
    const buffer = await fetchOptionalArrayBuffer(path);
    return buffer == null ? null : new TextDecoder().decode(buffer);
}

function bytesToBase64(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    const chunkSize = 0x8000;
    for (let index = 0; index < bytes.length; index += chunkSize) {
        binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
    }
    return btoa(binary);
}

function utf8ToArrayBuffer(text) {
    return new TextEncoder().encode(text).buffer;
}

function canonicalPointKey(point) { return `${point[0]},${point[1]}`; }

function canonicalEdgePoints(a, b) {
    if (a[0] < b[0]) return [a, b];
    if (a[0] > b[0]) return [b, a];
    return a[1] <= b[1] ? [a, b] : [b, a];
}

function edgeIdFromPoints(a, b) {
    const [first, second] = canonicalEdgePoints(a, b);
    return `${first[0]},${first[1]}:${second[0]},${second[1]}`;
}

function normalizeRing(ring) {
    const result = [];
    for (const point of ring) {
        if (!result.length || point[0] !== result[result.length - 1][0] || point[1] !== result[result.length - 1][1]) {
            result.push([point[0], point[1]]);
        }
    }
    if (result.length > 1 && result[0][0] === result[result.length - 1][0] && result[0][1] === result[result.length - 1][1]) {
        result.pop();
    }
    return result;
}

function projectGeo(latitude, longitude) {
    const clampedLat = Math.max(-85.05112878, Math.min(85.05112878, latitude));
    return {
        x: longitude * Math.PI / 180,
        y: -Math.log(Math.tan(Math.PI / 4 + clampedLat * Math.PI / 360)),
    };
}

function inverseProject(x, y) {
    return {
        longitude: x * 180 / Math.PI,
        latitude: (2 * Math.atan(Math.exp(-y)) - Math.PI / 2) * 180 / Math.PI,
    };
}

function distanceSquaredToSegment(point, edge) {
    const vx = edge.x2 - edge.x1;
    const vy = edge.y2 - edge.y1;
    const lengthSquared = vx * vx + vy * vy;
    if (lengthSquared === 0) return (point.x - edge.x1) ** 2 + (point.y - edge.y1) ** 2;
    const t = Math.max(0, Math.min(1, ((point.x - edge.x1) * vx + (point.y - edge.y1) * vy) / lengthSquared));
    const nx = edge.x1 + t * vx;
    const ny = edge.y1 + t * vy;
    return (point.x - nx) ** 2 + (point.y - ny) ** 2;
}

function parseMunicipalityGeometry(buffer) {
    const reader = new ByteReader(buffer);
    if (reader.readString(4) !== 'QDMB') throw new Error('Unexpected municipality geometry magic');
    const version = reader.readU32();
    if (![1, 2].includes(version)) throw new Error(`Unsupported QDMB version: ${version}`);
    const quantization = reader.readU32();
    const areaCount = reader.readU32();
    const areas = [];
    for (let areaIndex = 0; areaIndex < areaCount; areaIndex += 1) {
        const code = reader.readString(reader.readU16());
        const name = reader.readString(reader.readU16());
        const partCount = version >= 2 ? reader.readVarUint() : reader.readU32();
        const rings = [];
        for (let partIndex = 0; partIndex < partCount; partIndex += 1) {
            const pointCount = version >= 2 ? reader.readVarUint() : reader.readU32();
            let x = 0;
            let y = 0;
            const ring = [];
            for (let pointIndex = 0; pointIndex < pointCount; pointIndex += 1) {
                const dx = version >= 2 ? reader.readVarInt() : reader.readI32();
                const dy = version >= 2 ? reader.readVarInt() : reader.readI32();
                if (pointIndex === 0) {
                    x = dx;
                    y = dy;
                } else {
                    x += dx;
                    y += dy;
                }
                ring.push([x, y]);
            }
            const normalized = normalizeRing(ring);
            if (normalized.length >= 3) rings.push(normalized);
        }
        areas.push({ code, name, rings });
    }
    return { quantization, areas };
}

async function encodeMunicipalityGeometry(layer) {
    const writer = new ByteWriter();
    writer.writeBytes([0x51, 0x44, 0x4d, 0x42]); // QDMB
    writer.writeU32(2);
    writer.writeU32(layer.quantization);
    writer.writeU32(layer.areas.length);
    for (const area of layer.areas) {
        writer.writeString(area.code);
        writer.writeString(area.name);
        writer.writeVarUint(area.rings.length);
        for (const ring of area.rings) {
            writer.writeVarUint(ring.length);
            let previousX = 0;
            let previousY = 0;
            ring.forEach(([x, y], index) => {
                writer.writeVarInt(index === 0 ? x : x - previousX);
                writer.writeVarInt(index === 0 ? y : y - previousY);
                previousX = x;
                previousY = y;
            });
        }
    }
    return await gzip(writer.toUint8Array());
}

function parseBoundaryResource(buffer) {
    const reader = new ByteReader(buffer);
    if (reader.readString(4) !== 'QDMC') throw new Error('Unexpected boundary magic');
    const version = reader.readU32();
    if (version !== 1) throw new Error(`Unsupported QDMC version: ${version}`);
    const quantization = reader.readU32();
    const chunkCount = reader.readU32();
    const edges = new Set();
    function readPaths() {
        const pathCount = reader.readVarUint();
        for (let pathIndex = 0; pathIndex < pathCount; pathIndex += 1) {
            const pointCount = reader.readVarUint();
            let x = reader.readVarInt();
            let y = reader.readVarInt();
            let previous = [x, y];
            for (let pointIndex = 1; pointIndex < pointCount; pointIndex += 1) {
                x += reader.readVarInt();
                y += reader.readVarInt();
                const current = [x, y];
                if (previous[0] !== current[0] || previous[1] !== current[1]) edges.add(edgeIdFromPoints(previous, current));
                previous = current;
            }
        }
    }
    for (let chunkIndex = 0; chunkIndex < chunkCount; chunkIndex += 1) {
        reader.readVarInt();
        reader.readVarInt();
        readPaths();
    }
    readPaths();
    return { quantization, edges };
}

function gridFor(point, quantization) {
    const longitude = point[0] / quantization;
    const latitude = Math.max(-85.05112878, Math.min(85.05112878, point[1] / quantization));
    const x = longitude * Math.PI / 180;
    const y = -Math.log(Math.tan(Math.PI / 4 + latitude * Math.PI / 360));
    return [Math.floor(x / GRID_SIZE), Math.floor(y / GRID_SIZE)];
}

function chainEdges(edges) {
    const adjacency = new Map();
    edges.forEach(([a, b], index) => {
        const ka = canonicalPointKey(a);
        const kb = canonicalPointKey(b);
        if (!adjacency.has(ka)) adjacency.set(ka, []);
        if (!adjacency.has(kb)) adjacency.set(kb, []);
        adjacency.get(ka).push(index);
        adjacency.get(kb).push(index);
    });
    const unused = new Set(edges.map((_, index) => index));
    const paths = [];
    function follow(startPoint, firstEdge) {
        const path = [startPoint];
        let currentKey = canonicalPointKey(startPoint);
        let edgeIndex = firstEdge;
        while (true) {
            unused.delete(edgeIndex);
            const [first, second] = edges[edgeIndex];
            const nextPoint = canonicalPointKey(first) === currentKey ? second : first;
            path.push(nextPoint);
            currentKey = canonicalPointKey(nextPoint);
            const candidates = (adjacency.get(currentKey) || []).filter(candidate => unused.has(candidate));
            if ((adjacency.get(currentKey) || []).length !== 2 || !candidates.length) return path;
            edgeIndex = candidates[0];
        }
    }
    for (const [key, incident] of adjacency.entries()) {
        if (incident.length !== 2) {
            const start = key.split(',').map(Number);
            for (const edgeIndex of incident) if (unused.has(edgeIndex)) paths.push(follow(start, edgeIndex));
        }
    }
    while (unused.size) {
        const edgeIndex = Math.min(...unused);
        paths.push(follow(edges[edgeIndex][0], edgeIndex));
    }
    return paths;
}

function buildChunks(quantization, edges) {
    const chunks = new Map();
    const overflowEdges = [];
    for (const [a, b] of edges) {
        const ga = gridFor(a, quantization);
        const gb = gridFor(b, quantization);
        if (Math.abs(ga[0] - gb[0]) > 1 || Math.abs(ga[1] - gb[1]) > 1) {
            overflowEdges.push([a, b]);
            continue;
        }
        const midpoint = [Math.floor((a[0] + b[0]) / 2), Math.floor((a[1] + b[1]) / 2)];
        const key = gridFor(midpoint, quantization).join(',');
        if (!chunks.has(key)) chunks.set(key, []);
        chunks.get(key).push([a, b]);
    }
    const packed = [...chunks.entries()].map(([key, value]) => {
        const [x, y] = key.split(',').map(Number);
        return { x, y, paths: chainEdges(value) };
    }).sort((a, b) => a.x - b.x || a.y - b.y);
    return { chunks: packed, overflow: chainEdges(overflowEdges) };
}

function writePaths(writer, paths) {
    writer.writeVarUint(paths.length);
    for (const path of paths) {
        writer.writeVarUint(path.length);
        let [x, y] = path[0];
        writer.writeVarInt(x);
        writer.writeVarInt(y);
        for (let index = 1; index < path.length; index += 1) {
            const [nx, ny] = path[index];
            writer.writeVarInt(nx - x);
            writer.writeVarInt(ny - y);
            x = nx;
            y = ny;
        }
    }
}

async function encodeBoundaryResource(layer, className) {
    const edges = [];
    for (const edge of layer.edges.values()) {
        if (edge.ownerIndexes?.length === 2 && edge.currentClass === className) edges.push(edge.points);
    }
    const { chunks, overflow } = buildChunks(layer.quantization, edges);
    const writer = new ByteWriter();
    writer.writeBytes([0x51, 0x44, 0x4d, 0x43]); // QDMC
    writer.writeU32(1);
    writer.writeU32(layer.quantization);
    writer.writeU32(chunks.length);
    for (const chunk of chunks) {
        writer.writeVarInt(chunk.x);
        writer.writeVarInt(chunk.y);
        writePaths(writer, chunk.paths);
    }
    writePaths(writer, overflow);
    return await gzip(writer.toUint8Array());
}

function parseJmaAreaRoot(root) {
    const quantization = Number(root.quantization);
    const areas = root.areas.map(area => {
        const rings = area[2].map(encoded => {
            let x = encoded[0];
            let y = encoded[1];
            const ring = [[x, y]];
            for (let offset = 2; offset + 1 < encoded.length; offset += 2) {
                x += encoded[offset];
                y += encoded[offset + 1];
                ring.push([x, y]);
            }
            return normalizeRing(ring);
        }).filter(ring => ring.length >= 3);
        return { code: String(area[0]), name: String(area[1]), rings };
    });
    return { quantization, areas, root };
}

function encodeJmaAreaRoot(layer) {
    const encodeRing = ring => {
        const encoded = [ring[0][0], ring[0][1]];
        let [previousX, previousY] = ring[0];
        for (let index = 1; index < ring.length; index += 1) {
            const [x, y] = ring[index];
            encoded.push(x - previousX, y - previousY);
            previousX = x;
            previousY = y;
        }
        return encoded;
    };
    return {
        ...(layer.sourceRoot || {}),
        quantization: layer.quantization,
        closed: true,
        areas: layer.areas.map(area => [area.code, area.name, area.rings.map(encodeRing)]),
    };
}

function parseReportingBorders(buffer) {
    const reader = new ByteReader(buffer);
    if (reader.readString(4) !== 'QDBP') throw new Error('Unexpected JMA reporting-border magic');
    const version = reader.readU32();
    if (version !== 1) throw new Error(`Unsupported QDBP version: ${version}`);
    const quantization = reader.readU32();
    const areaCount = reader.readU32();
    const prefectureEdges = new Set();
    const readPaths = collect => {
        const pathCount = reader.readVarUint();
        for (let pathIndex = 0; pathIndex < pathCount; pathIndex += 1) {
            const pointCount = reader.readVarUint();
            let x = reader.readVarInt();
            let y = reader.readVarInt();
            let previous = [x, y];
            for (let pointIndex = 1; pointIndex < pointCount; pointIndex += 1) {
                x += reader.readVarInt();
                y += reader.readVarInt();
                const current = [x, y];
                if (collect && (previous[0] !== current[0] || previous[1] !== current[1])) collect.add(edgeIdFromPoints(previous, current));
                previous = current;
            }
        }
    };
    for (let areaIndex = 0; areaIndex < areaCount; areaIndex += 1) {
        readPaths(null);
        readPaths(prefectureEdges);
    }
    return { quantization, areaCount, prefectureEdges };
}

function splitRingByFlags(ring, flags) {
    const count = ring.length;
    if (!count || !flags.some(Boolean)) return [];
    if (flags.every(Boolean)) return [ring.concat([ring[0]])];
    const paths = [];
    for (let start = 0; start < count; start += 1) {
        if (!flags[start] || flags[(start - 1 + count) % count]) continue;
        const path = [ring[start]];
        let edgeIndex = start;
        while (flags[edgeIndex]) {
            path.push(ring[(edgeIndex + 1) % count]);
            edgeIndex = (edgeIndex + 1) % count;
        }
        paths.push(path);
    }
    return paths;
}

async function encodeReportingBorders(layer) {
    const writer = new ByteWriter();
    writer.writeBytes([0x51, 0x44, 0x42, 0x50]); // QDBP
    writer.writeU32(1);
    writer.writeU32(layer.quantization);
    writer.writeU32(layer.areas.length);
    const emitted = new Set();
    for (let areaIndex = 0; areaIndex < layer.areas.length; areaIndex += 1) {
        const area = layer.areas[areaIndex];
        const finePaths = [];
        const prefecturePaths = [];
        for (const ring of area.rings) {
            const fineFlags = [];
            const prefectureFlags = [];
            for (let index = 0; index < ring.length; index += 1) {
                const id = edgeIdFromPoints(ring[index], ring[(index + 1) % ring.length]);
                const edge = layer.edges.get(id);
                const isShared = edge?.ownerIndexes?.length === 2;
                const areaOwnsEdge = isShared && edge.ownerIndexes.includes(areaIndex);
                const firstOccurrence = areaOwnsEdge && !emitted.has(id);
                const isPrefecture = firstOccurrence && edge?.currentClass === 'prefecture';
                const isFine = firstOccurrence && edge?.currentClass === 'fine';
                fineFlags.push(isFine);
                prefectureFlags.push(isPrefecture);
                if (isFine || isPrefecture) emitted.add(id);
            }
            finePaths.push(...splitRingByFlags(ring, fineFlags));
            prefecturePaths.push(...splitRingByFlags(ring, prefectureFlags));
        }
        for (const edge of layer.edges.values()) {
            if (!edge.isManual || edge.ownerIndexes.length !== 2 || edge.ownerIndexes[0] !== areaIndex) continue;
            if (edge.currentClass === 'fine') finePaths.push(edge.points.map(point => [point[0], point[1]]));
            else if (edge.currentClass === 'prefecture') prefecturePaths.push(edge.points.map(point => [point[0], point[1]]));
        }
        writePaths(writer, finePaths);
        writePaths(writer, prefecturePaths);
    }
    return await gzip(writer.toUint8Array());
}

function parseCoastlines(buffer) {
    const reader = new ByteReader(buffer);
    if (reader.readU32() !== 0x5144434c) throw new Error('Unexpected coastline resource magic');
    const version = reader.readU32();
    if (version !== 1) throw new Error(`Unsupported coastline version: ${version}`);
    const quantization = reader.readI32();
    const prefectureCount = reader.readI32();
    const segments = [];
    for (let prefectureIndex = 0; prefectureIndex < prefectureCount; prefectureIndex += 1) {
        const name = reader.readString(reader.readI32());
        const segmentCount = reader.readI32();
        for (let segmentIndex = 0; segmentIndex < segmentCount; segmentIndex += 1) {
            const pointCount = reader.readI32();
            const points = [];
            for (let pointIndex = 0; pointIndex < pointCount; pointIndex += 1) {
                points.push({ x: reader.readI32() / quantization, y: reader.readI32() / quantization });
            }
            if (points.length >= 2) segments.push({ prefecture: name, points });
        }
    }
    return segments;
}

function ownerKeyForAreas(areas) {
    return areas.map(area => area.code).sort().join('|');
}

function buildLayerModel(layer, previousEdges = null) {
    const oldByOwner = new Map();
    if (previousEdges) {
        for (const edge of previousEdges.values()) {
            if (!oldByOwner.has(edge.ownerKey)) oldByOwner.set(edge.ownerKey, []);
            oldByOwner.get(edge.ownerKey).push(edge);
        }
    }

    const edges = new Map();
    const vertices = new Map();
    const areaBounds = new Map();
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    const addVertex = (point, areaIndex, ringIndex, pointIndex) => {
        const key = canonicalPointKey(point);
        let vertex = vertices.get(key);
        if (!vertex) {
            const projected = projectGeo(point[1] / layer.quantization, point[0] / layer.quantization);
            vertex = { key, point: [point[0], point[1]], x: projected.x, y: projected.y, occurrences: [] };
            vertices.set(key, vertex);
            minX = Math.min(minX, vertex.x);
            minY = Math.min(minY, vertex.y);
            maxX = Math.max(maxX, vertex.x);
            maxY = Math.max(maxY, vertex.y);
        }
        vertex.occurrences.push({ areaIndex, ringIndex, pointIndex });
    };

    layer.areas.forEach((area, areaIndex) => {
        let areaMinX = Infinity;
        let areaMinY = Infinity;
        let areaMaxX = -Infinity;
        let areaMaxY = -Infinity;
        area.rings.forEach((ring, ringIndex) => {
            ring.forEach((point, pointIndex) => {
                addVertex(point, areaIndex, ringIndex, pointIndex);
                const projected = projectGeo(point[1] / layer.quantization, point[0] / layer.quantization);
                areaMinX = Math.min(areaMinX, projected.x);
                areaMinY = Math.min(areaMinY, projected.y);
                areaMaxX = Math.max(areaMaxX, projected.x);
                areaMaxY = Math.max(areaMaxY, projected.y);
            });
            for (let edgeIndex = 0; edgeIndex < ring.length; edgeIndex += 1) {
                const first = ring[edgeIndex];
                const second = ring[(edgeIndex + 1) % ring.length];
                if (first[0] === second[0] && first[1] === second[1]) continue;
                const id = edgeIdFromPoints(first, second);
                let edge = edges.get(id);
                if (!edge) {
                    const [a, b] = canonicalEdgePoints(first, second);
                    const pa = projectGeo(a[1] / layer.quantization, a[0] / layer.quantization);
                    const pb = projectGeo(b[1] / layer.quantization, b[0] / layer.quantization);
                    edge = {
                        id,
                        points: [[a[0], a[1]], [b[0], b[1]]],
                        ownerIndexes: [],
                        occurrences: [],
                        x1: pa.x, y1: pa.y, x2: pb.x, y2: pb.y,
                        minX: Math.min(pa.x, pb.x), minY: Math.min(pa.y, pb.y),
                        maxX: Math.max(pa.x, pb.x), maxY: Math.max(pa.y, pb.y),
                        midX: (pa.x + pb.x) / 2, midY: (pa.y + pb.y) / 2,
                    };
                    edges.set(id, edge);
                }
                if (!edge.ownerIndexes.includes(areaIndex)) edge.ownerIndexes.push(areaIndex);
                edge.occurrences.push({ areaIndex, ringIndex, edgeIndex });
            }
        });
        areaBounds.set(area.code, { minX: areaMinX, minY: areaMinY, maxX: areaMaxX, maxY: areaMaxY });
    });

    const areaIndexByCode = new Map(layer.areas.map((area, index) => [area.code, index]));
    for (const edge of edges.values()) edge.rawOwnerIndexes = [...edge.ownerIndexes];

    for (const manual of layer.manualEdges?.values() || []) {
        const first = manual.points?.[0];
        const second = manual.points?.[1];
        if (!first || !second || canonicalPointKey(first) === canonicalPointKey(second)) continue;
        const id = manual.id || edgeIdFromPoints(first, second);
        if (edges.has(id)) continue;
        const [a, b] = canonicalEdgePoints(first, second);
        const pa = projectGeo(a[1] / layer.quantization, a[0] / layer.quantization);
        const pb = projectGeo(b[1] / layer.quantization, b[0] / layer.quantization);
        const ownerIndexes = (manual.owners || []).map(code => areaIndexByCode.get(String(code))).filter(index => index != null);
        edges.set(id, {
            id,
            points: [[a[0], a[1]], [b[0], b[1]]],
            ownerIndexes,
            rawOwnerIndexes: [],
            occurrences: [],
            isManual: true,
            x1: pa.x, y1: pa.y, x2: pb.x, y2: pb.y,
            minX: Math.min(pa.x, pb.x), minY: Math.min(pa.y, pb.y),
            maxX: Math.max(pa.x, pb.x), maxY: Math.max(pa.y, pb.y),
            midX: (pa.x + pb.x) / 2, midY: (pa.y + pb.y) / 2,
        });
    }

    for (const edge of edges.values()) {
        edge.rawOwnerIndexes ??= [...edge.ownerIndexes];
        const overrideCodes = layer.ownerOverrideById?.get(edge.id);
        if (overrideCodes) {
            edge.ownerIndexes = overrideCodes.map(code => areaIndexByCode.get(String(code))).filter(index => index != null);
            edge.ownerOverrideCodes = [...overrideCodes];
        } else if (edge.isManual) {
            const manual = layer.manualEdges?.get(edge.id);
            edge.ownerIndexes = (manual?.owners || []).map(code => areaIndexByCode.get(String(code))).filter(index => index != null);
        }
    }

    const findInherited = edge => {
        if (!previousEdges) return null;
        const exact = previousEdges.get(edge.id);
        if (exact) return exact;
        const candidates = oldByOwner.get(edge.ownerKey) || [];
        if (!candidates.length) return null;
        let best = null;
        let bestDistance = Infinity;
        for (const candidate of candidates) {
            const distance = distanceSquaredToSegment({ x: edge.midX, y: edge.midY }, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    };

    for (const edge of edges.values()) {
        edge.ownerAreas = edge.ownerIndexes.map(index => layer.areas[index]).filter(Boolean);
        edge.rawOwnerAreas = edge.rawOwnerIndexes.map(index => layer.areas[index]).filter(Boolean);
        edge.ownerKey = ownerKeyForAreas(edge.ownerAreas);
        const inherited = findInherited(edge);
        const loadedClass = layer.loadedClassById?.get(edge.id);
        const explicitOverrideClass = layer.explicitOverrideClassById?.get(edge.id);
        const manual = layer.manualEdges?.get(edge.id);
        const automaticCoast = layer.coastAllExterior && edge.ownerIndexes.length === 1 && !explicitOverrideClass;
        edge.currentClass = inherited?.currentClass || explicitOverrideClass || (automaticCoast ? 'coast' : loadedClass || manual?.class || 'fine');
        edge.baselineClass = inherited?.baselineClass || (automaticCoast ? 'coast' : layer.baselineClassById?.get(edge.id) || manual?.baselineClass || edge.currentClass);
        edge.savedClass = inherited?.savedClass || (automaticCoast ? 'coast' : layer.savedClassById?.get(edge.id) || manual?.class || edge.currentClass);
    }

    const adjacency = new Map();
    for (const edge of edges.values()) {
        const [a, b] = edge.points;
        const ka = canonicalPointKey(a);
        const kb = canonicalPointKey(b);
        const length = Math.hypot(edge.x2 - edge.x1, edge.y2 - edge.y1);
        if (!adjacency.has(ka)) adjacency.set(ka, []);
        if (!adjacency.has(kb)) adjacency.set(kb, []);
        adjacency.get(ka).push({ key: kb, edgeId: edge.id, length });
        adjacency.get(kb).push({ key: ka, edgeId: edge.id, length });
    }

    const edgeAdjacency = new Map();
    for (const edge of edges.values()) edgeAdjacency.set(edge.id, new Set());
    for (const incident of adjacency.values()) {
        const edgeIds = [...new Set(incident.map(item => item.edgeId))];
        for (let i = 0; i < edgeIds.length; i += 1) {
            for (let j = i + 1; j < edgeIds.length; j += 1) {
                edgeAdjacency.get(edgeIds[i])?.add(edgeIds[j]);
                edgeAdjacency.get(edgeIds[j])?.add(edgeIds[i]);
            }
        }
    }

    layer.edges = edges;
    layer.vertices = vertices;
    layer.adjacency = adjacency;
    layer.edgeAdjacency = edgeAdjacency;
    layer.bounds = { minX, minY, maxX, maxY };
    layer.areaBounds = areaBounds;
    buildSpatialIndexes(layer);
    buildAreaSpatialIndex(layer);
    refreshTopologyIssues(layer, previousEdges);
}

function buildSpatialIndexes(layer) {
    layer.edgeSpatialIndex = new Map();
    for (const edge of layer.edges.values()) {
        const minGX = Math.floor(edge.minX / GRID_SIZE);
        const maxGX = Math.floor(edge.maxX / GRID_SIZE);
        const minGY = Math.floor(edge.minY / GRID_SIZE);
        const maxGY = Math.floor(edge.maxY / GRID_SIZE);
        for (let gx = minGX; gx <= maxGX; gx += 1) {
            for (let gy = minGY; gy <= maxGY; gy += 1) {
                const key = `${gx},${gy}`;
                if (!layer.edgeSpatialIndex.has(key)) layer.edgeSpatialIndex.set(key, []);
                layer.edgeSpatialIndex.get(key).push(edge.id);
            }
        }
    }
    layer.vertexSpatialIndex = new Map();
    for (const vertex of layer.vertices.values()) {
        const key = `${Math.floor(vertex.x / VERTEX_GRID_SIZE)},${Math.floor(vertex.y / VERTEX_GRID_SIZE)}`;
        if (!layer.vertexSpatialIndex.has(key)) layer.vertexSpatialIndex.set(key, []);
        layer.vertexSpatialIndex.get(key).push(vertex.key);
    }
}

function buildAreaSpatialIndex(layer) {
    layer.areaSpatialIndex = new Map();
    layer.areas.forEach((area, areaIndex) => {
        const bounds = layer.areaBounds.get(area.code);
        if (!bounds || !Number.isFinite(bounds.minX)) return;
        const minGX = Math.floor(bounds.minX / AREA_GRID_SIZE);
        const maxGX = Math.floor(bounds.maxX / AREA_GRID_SIZE);
        const minGY = Math.floor(bounds.minY / AREA_GRID_SIZE);
        const maxGY = Math.floor(bounds.maxY / AREA_GRID_SIZE);
        for (let gx = minGX; gx <= maxGX; gx += 1) {
            for (let gy = minGY; gy <= maxGY; gy += 1) {
                const key = `${gx},${gy}`;
                if (!layer.areaSpatialIndex.has(key)) layer.areaSpatialIndex.set(key, []);
                layer.areaSpatialIndex.get(key).push(areaIndex);
            }
        }
    });
}

function pointInRingQuantized(point, ring) {
    let inside = false;
    const [px, py] = point;
    for (let index = 0, previous = ring.length - 1; index < ring.length; previous = index, index += 1) {
        const [xi, yi] = ring[index];
        const [xj, yj] = ring[previous];
        const crosses = (yi > py) !== (yj > py);
        if (!crosses) continue;
        const crossingX = (xj - xi) * (py - yi) / (yj - yi) + xi;
        if (px < crossingX) inside = !inside;
    }
    return inside;
}

function areaContainsWorldPoint(layer, areaIndex, x, y) {
    const area = layer.areas[areaIndex];
    if (!area) return false;
    const bounds = layer.areaBounds.get(area.code);
    if (!bounds || x < bounds.minX || x > bounds.maxX || y < bounds.minY || y > bounds.maxY) return false;
    const geo = inverseProject(x, y);
    const qPoint = [geo.longitude * layer.quantization, geo.latitude * layer.quantization];
    let inside = false;
    for (const ring of area.rings) {
        if (pointInRingQuantized(qPoint, ring)) inside = !inside;
    }
    return inside;
}

function areaIndexesAtWorldPoint(layer, x, y, limit = Infinity) {
    const gx = Math.floor(x / AREA_GRID_SIZE);
    const gy = Math.floor(y / AREA_GRID_SIZE);
    const candidates = layer.areaSpatialIndex?.get(`${gx},${gy}`) || [];
    const result = [];
    for (const areaIndex of candidates) {
        if (!areaContainsWorldPoint(layer, areaIndex, x, y)) continue;
        result.push(areaIndex);
        if (result.length >= limit) break;
    }
    return result;
}

function isInternalSingleOwnerEdge(layer, edge) {
    if (edge.ownerIndexes.length !== 1) return false;
    const dx = edge.x2 - edge.x1;
    const dy = edge.y2 - edge.y1;
    const length = Math.hypot(dx, dy);
    if (length <= 1e-14) return true;
    const unitNX = -dy / length;
    const unitNY = dx / length;
    const quantStep = (Math.PI / 180) / layer.quantization;
    const offset = Math.min(length * 0.18, Math.max(quantStep * 8, 1e-10));
    for (const ratio of [0.25, 0.5, 0.75]) {
        const x = edge.x1 + dx * ratio;
        const y = edge.y1 + dy * ratio;
        const firstCovered = areaIndexesAtWorldPoint(layer, x + unitNX * offset, y + unitNY * offset, 1).length > 0;
        const secondCovered = areaIndexesAtWorldPoint(layer, x - unitNX * offset, y - unitNY * offset, 1).length > 0;
        if (firstCovered && secondCovered) return true;
    }
    return false;
}

function refreshTopologyIssues(layer, previousEdges = null) {
    const issues = [];
    for (const edge of layer.edges.values()) {
        const ownerCount = edge.ownerIndexes.length;
        if (ownerCount === 0) {
            issues.push({ edgeId: edge.id, type: 'no-owner', severity: 'error', label: 'Edge has no owners' });
        } else if (ownerCount > 2) {
            issues.push({ edgeId: edge.id, type: 'too-many-owners', severity: 'error', label: `${ownerCount}-owner edge` });
        } else if (ownerCount === 1) {
            if (edge.currentClass !== 'coast') {
                issues.push({ edgeId: edge.id, type: 'unassigned-coast', severity: 'error', label: 'One-owner edge is not marked Coast' });
            }
        } else if (edge.currentClass === 'coast') {
            issues.push({ edgeId: edge.id, type: 'coast-two-owners', severity: 'error', label: 'Coast edge has two owners' });
        }
    }
    layer.topologyIssues = issues;
    if (elements.issuesButton && activeLayer() === layer) updateTopologyIssueUi();
    return issues;
}

function updateTopologyIssueUi() {
    const layer = activeLayer();
    if (!layer || !elements.issuesButton) return;
    const issues = layer.topologyIssues || [];
    elements.issuesButton.textContent = `Issues: ${issues.length}`;
    elements.issuesButton.classList.toggle('has-issues', issues.length > 0);
    elements.issuesButton.disabled = issues.length === 0;
    elements.topologySummary.textContent = issues.length
        ? `${issues.length} topology error${issues.length === 1 ? '' : 's'}. Red lines are invalid edges.`
        : 'No topology errors detected.';
    elements.topologyIssueList.innerHTML = '';
    elements.topologyIssueList.className = issues.length ? 'result-list' : 'result-list empty';
    if (!issues.length) {
        elements.topologyIssueList.textContent = 'No topology issues.';
        return;
    }
    for (const issue of issues.slice(0, 100)) {
        const edge = layer.edges.get(issue.edgeId);
        if (!edge) continue;
        const item = document.createElement('div');
        item.className = `result-item issue-item ${issue.severity === 'warning' ? 'warning' : ''}`;
        const owners = edge.ownerAreas.map(area => area.nameEn || area.name).join(' / ') || 'no owners';
        item.innerHTML = `<strong>${issue.label}</strong><small>${owners} • ${issue.edgeId}</small>`;
        item.addEventListener('click', () => {
            setSelectedEdge(edge);
            focusSelectedEdge();
        });
        elements.topologyIssueList.appendChild(item);
    }
}

function focusNextTopologyIssue() {
    const layer = activeLayer();
    const issues = layer?.topologyIssues || [];
    if (!issues.length) return;
    state.issueCursor = (state.issueCursor + 1) % issues.length;
    const edge = layer.edges.get(issues[state.issueCursor].edgeId);
    if (!edge) return;
    setSelectedEdge(edge);
    focusSelectedEdge();
    setStatus(`${issues[state.issueCursor].label} (${state.issueCursor + 1}/${issues.length}).`);
}

function topologyIssueSuffix(layer) {
    const count = layer?.topologyIssues?.length || 0;
    return count ? ` WARNING: ${count} topology issue${count === 1 ? '' : 's'} detected.` : '';
}

function shortestVertexPath(layer, startKey, targetKey) {
    if (startKey === targetKey) return [startKey];
    if (!layer.vertices.has(startKey) || !layer.vertices.has(targetKey)) return [];
    const target = layer.vertices.get(targetKey);
    const heap = new MinHeap();
    const distances = new Map([[startKey, 0]]);
    const previous = new Map();
    const closed = new Set();
    const heuristic = key => {
        const vertex = layer.vertices.get(key);
        return Math.hypot(vertex.x - target.x, vertex.y - target.y);
    };
    heap.push({ key: startKey, distance: 0, priority: heuristic(startKey) });
    while (heap.size) {
        const current = heap.pop();
        if (closed.has(current.key)) continue;
        if (current.key === targetKey) break;
        closed.add(current.key);
        for (const neighbor of layer.adjacency.get(current.key) || []) {
            const nextDistance = current.distance + neighbor.length;
            if (nextDistance >= (distances.get(neighbor.key) ?? Infinity)) continue;
            distances.set(neighbor.key, nextDistance);
            previous.set(neighbor.key, current.key);
            heap.push({ key: neighbor.key, distance: nextDistance, priority: nextDistance + heuristic(neighbor.key) });
        }
    }
    if (!previous.has(targetKey)) return [];
    const path = [targetKey];
    let current = targetKey;
    while (current !== startKey) {
        current = previous.get(current);
        if (!current) return [];
        path.push(current);
    }
    path.reverse();
    return path;
}

function shortestEdgePath(layer, startEdgeId, targetEdgeId) {
    if (startEdgeId === targetEdgeId) return [startEdgeId];
    if (!layer.edges.has(startEdgeId) || !layer.edges.has(targetEdgeId)) return [];
    const heap = new MinHeap();
    const distances = new Map([[startEdgeId, 0]]);
    const previous = new Map();
    const closed = new Set();
    heap.push({ key: startEdgeId, distance: 0, priority: 0 });
    while (heap.size) {
        const current = heap.pop();
        if (closed.has(current.key)) continue;
        if (current.key === targetEdgeId) break;
        closed.add(current.key);
        for (const neighborId of layer.edgeAdjacency.get(current.key) || []) {
            const neighbor = layer.edges.get(neighborId);
            if (!neighbor) continue;
            const edgeLength = Math.hypot(neighbor.x2 - neighbor.x1, neighbor.y2 - neighbor.y1);
            const nextDistance = current.distance + edgeLength;
            if (nextDistance >= (distances.get(neighborId) ?? Infinity)) continue;
            distances.set(neighborId, nextDistance);
            previous.set(neighborId, current.key);
            heap.push({ key: neighborId, distance: nextDistance, priority: nextDistance });
        }
    }
    if (!previous.has(targetEdgeId)) return [];
    const path = [targetEdgeId];
    let current = targetEdgeId;
    while (current !== startEdgeId) {
        current = previous.get(current);
        if (!current) return [];
        path.push(current);
    }
    path.reverse();
    return path;
}

const state = {
    meta: null,
    translations: null,
    basemapTiles: new Map(),
    layers: new Map(),
    activeLayerKey: 'municipality',
    selectedEdgeId: null,
    selectedEdgeIds: new Set(),
    primaryEdgeId: null,
    selectedVertices: new Set(),
    primaryVertexKey: null,
    searchHighlightCode: null,
    viewport: { scale: 1, offsetX: 0, offsetY: 0, fitScale: 1 },
    show: { basemap: true, coast: true, fine: true, warning: true, prefecture: true, vertices: true, modified: true, errors: true, labels: true },
    advanced: false,
    moveMode: false,
    addPointMode: false,
    areaOperation: null,
    selectedAreaCodes: new Set(),
    hoverAreaCode: null,
    issueCursor: -1,
    pointer: null,
    selectionBox: null,
    movePreview: null,
    undoStack: [],
    redoStack: [],
    restoringHistory: false,
};

const elements = {};

function activeLayer() { return state.layers.get(state.activeLayerKey); }
function zoomRatio() { return state.viewport.scale / Math.max(state.viewport.fitScale, 1e-9); }
function setStatus(text) { elements.statusText.textContent = text; }


function cloneRings(rings) {
    return rings.map(ring => ring.map(point => [point[0], point[1]]));
}

function ensureSessionBaseForCodes(layer, codes) {
    if (!layer.sessionBaseRings) layer.sessionBaseRings = new Map();
    for (const code of codes) {
        if (layer.sessionBaseRings.has(code)) continue;
        const area = layer.areas.find(item => item.code === code);
        if (area) layer.sessionBaseRings.set(code, cloneRings(area.rings));
    }
}

function historySnapshot(label = '') {
    const layer = activeLayer();
    if (!layer) return null;
    return {
        label,
        layerKey: layer.key,
        payload: currentOverridePayload(layer),
        geometryRevision: layer.geometryRevision,
        topologyRevision: layer.topologyRevision || 0,
        selectedEdgeIds: [...state.selectedEdgeIds],
        primaryEdgeId: state.primaryEdgeId,
        selectedVertices: [...state.selectedVertices],
        primaryVertexKey: state.primaryVertexKey,
    };
}

function recordHistory(label) {
    if (state.restoringHistory) return;
    const snapshot = historySnapshot(label);
    if (!snapshot) return;
    state.undoStack.push(snapshot);
    if (state.undoStack.length > HISTORY_LIMIT) state.undoStack.shift();
    state.redoStack.length = 0;
    updateHistoryUi();
}

function updateHistoryUi() {
    if (!elements.undoButton || !elements.redoButton) return;
    elements.undoButton.disabled = state.undoStack.length === 0;
    elements.redoButton.disabled = state.redoStack.length === 0;
    elements.undoButton.title = state.undoStack.length ? `Undo ${state.undoStack.at(-1).label || 'edit'} (Ctrl+Z)` : 'Undo (Ctrl+Z)';
    elements.redoButton.title = state.redoStack.length ? `Redo ${state.redoStack.at(-1).label || 'edit'} (Ctrl+Y / Ctrl+Shift+Z)` : 'Redo (Ctrl+Y / Ctrl+Shift+Z)';
}

function applyHistoryPayload(layer, snapshot) {
    const payload = snapshot.payload || { geometryAreas: [], geometryEdgeClasses: [], overrides: [] };
    if (layer.sessionBaseRings) {
        for (const [code, rings] of layer.sessionBaseRings.entries()) {
            const area = layer.areas.find(item => item.code === code);
            if (area) area.rings = cloneRings(rings);
        }
    }
    const geometryByCode = new Map((payload.geometryAreas || []).map(item => [String(item.code), item]));
    for (const area of layer.areas) {
        const override = geometryByCode.get(area.code);
        if (override?.rings) area.rings = cloneRings(override.rings);
    }
    layer.changedAreaCodes = new Set((payload.geometryAreas || []).map(item => String(item.code)));
    layer.geometryRevision = snapshot.geometryRevision ?? layer.geometryRevision;
    layer.topologyRevision = snapshot.topologyRevision ?? layer.topologyRevision ?? 0;
    applyTopologyOverridePayload(layer, payload);
    buildLayerModel(layer);

    for (const item of payload.geometryEdgeClasses || []) {
        const edge = layer.edges.get(item.id);
        if (!edge) continue;
        edge.currentClass = item.class || edge.currentClass;
        edge.baselineClass = item.baselineClass || edge.baselineClass;
    }
    for (const item of payload.overrides || []) {
        const edge = layer.edges.get(item.id);
        if (!edge) continue;
        if (item.from) edge.baselineClass = item.from;
        if (item.to) edge.currentClass = item.to;
    }

    state.selectedEdgeIds = new Set(snapshot.selectedEdgeIds.filter(id => layer.edges.has(id)));
    state.primaryEdgeId = layer.edges.has(snapshot.primaryEdgeId) ? snapshot.primaryEdgeId : [...state.selectedEdgeIds].at(-1) || null;
    state.selectedEdgeId = state.primaryEdgeId;
    state.selectedVertices = new Set(snapshot.selectedVertices.filter(key => layer.vertices.has(key)));
    state.primaryVertexKey = layer.vertices.has(snapshot.primaryVertexKey) ? snapshot.primaryVertexKey : [...state.selectedVertices].at(-1) || null;
    state.moveMode = false;
    state.addPointMode = false;
    state.areaOperation = null;
    state.selectedAreaCodes.clear();
    state.hoverAreaCode = null;
    state.movePreview = null;
    updateModifiedUi();
    updateSelectionUi();
    render();
}

function undoEdit() {
    if (!state.undoStack.length) return;
    const target = state.undoStack.pop();
    const layer = state.layers.get(target.layerKey);
    if (!layer) return;
    if (state.activeLayerKey !== target.layerKey) {
        state.activeLayerKey = target.layerKey;
        elements.layerSelect.value = target.layerKey;
        syncLayerControlLabels();
    }
    const current = historySnapshot(target.label);
    if (current) state.redoStack.push(current);
    state.restoringHistory = true;
    try {
        applyHistoryPayload(layer, target);
        setStatus(`Undid ${target.label || 'edit'}.`);
    } finally {
        state.restoringHistory = false;
        updateHistoryUi();
    }
}

function redoEdit() {
    if (!state.redoStack.length) return;
    const target = state.redoStack.pop();
    const layer = state.layers.get(target.layerKey);
    if (!layer) return;
    if (state.activeLayerKey !== target.layerKey) {
        state.activeLayerKey = target.layerKey;
        elements.layerSelect.value = target.layerKey;
        syncLayerControlLabels();
    }
    const current = historySnapshot(target.label);
    if (current) state.undoStack.push(current);
    state.restoringHistory = true;
    try {
        applyHistoryPayload(layer, target);
        setStatus(`Redid ${target.label || 'edit'}.`);
    } finally {
        state.restoringHistory = false;
        updateHistoryUi();
    }
}

function updateZoomText() {
    elements.zoomText.textContent = `${zoomRatio().toFixed(1)}× editor zoom`;
}

function layerModifiedCounts(layer) {
    let overrides = 0;
    let unsavedClasses = 0;
    for (const edge of layer.edges.values()) {
        const automaticCoast = layer.coastAllExterior && edge.ownerIndexes.length === 1 && edge.currentClass === 'coast';
        if (!automaticCoast && edge.currentClass !== edge.baselineClass) overrides += 1;
        if (edge.currentClass !== edge.savedClass) unsavedClasses += 1;
    }
    if (layer.coastAllExterior) overrides += 1;
    const unsavedGeometry = layer.geometryRevision !== layer.savedGeometryRevision;
    const unsavedTopology = (layer.topologyRevision || 0) !== (layer.savedTopologyRevision || 0);
    return { overrides, geometryAreas: layer.changedAreaCodes?.size || 0, unsavedClasses, unsavedGeometry, unsavedTopology };
}

function updateModifiedUi() {
    const layer = activeLayer();
    if (!layer) return;
    const counts = layerModifiedCounts(layer);
    const geometryLabel = counts.geometryAreas ? ` + ${counts.geometryAreas} area${counts.geometryAreas === 1 ? '' : 's'}` : '';
    const dirtyMark = counts.unsavedGeometry || counts.unsavedTopology ? '*' : '';
    elements.modifiedCount.textContent = `${counts.overrides}${geometryLabel}${dirtyMark}`;
    elements.saveButton.disabled = false;
}

function updateSelectionUi() {
    const layer = activeLayer();
    elements.selectedVorticeCount.textContent = String(state.selectedEdgeIds.size);
    elements.selectedPrimaryVortice.textContent = state.primaryEdgeId || '—';
    elements.selectedPointCount.textContent = String(state.selectedVertices.size);
    elements.selectedPrimaryPoint.textContent = state.primaryVertexKey || '—';

    const selectedEdges = [...state.selectedEdgeIds].map(id => layer?.edges.get(id)).filter(Boolean);
    const edge = layer?.edges.get(state.primaryEdgeId) || selectedEdges[0] || null;
    state.selectedEdgeId = edge?.id || null;
    if (!edge) {
        elements.selectionEmpty.classList.remove('hidden');
        elements.selectionDetails.classList.add('hidden');
        document.querySelectorAll('.class-button').forEach(button => {
            button.classList.remove('active-class');
            button.disabled = true;
        });
        elements.revertButton.disabled = true;
        elements.focusButton.disabled = true;
        elements.reassignAreasButton.disabled = true;
    } else {
        elements.revertButton.disabled = false;
        elements.focusButton.disabled = false;
        elements.reassignAreasButton.disabled = false;
        elements.selectionEmpty.classList.add('hidden');
        elements.selectionDetails.classList.remove('hidden');
        const ownerText = edge.ownerAreas.length
            ? edge.ownerAreas.map(area => `${area.name}${area.nameEn && area.nameEn !== area.name ? ` / ${area.nameEn}` : ''}`).join(' ↔ ')
            : '—';
        elements.selectedOwners.textContent = selectedEdges.length > 1 ? `${selectedEdges.length} vortices selected; primary: ${ownerText}` : ownerText;
        const rawOwnerText = edge.rawOwnerAreas?.length
            ? edge.rawOwnerAreas.map(area => `${area.name}${area.nameEn && area.nameEn !== area.name ? ` / ${area.nameEn}` : ''}`).join(' ↔ ')
            : edge.isManual ? 'Manual edge' : '—';
        elements.selectedRawOwners.textContent = rawOwnerText;
        const currentClasses = new Set(selectedEdges.map(item => item.currentClass));
        const baselineClasses = new Set(selectedEdges.map(item => item.baselineClass));
        elements.selectedCurrent.textContent = currentClasses.size === 1 ? classLabel(layer, [...currentClasses][0]) : 'Mixed';
        elements.selectedOriginal.textContent = baselineClasses.size === 1 ? classLabel(layer, [...baselineClasses][0]) : 'Mixed';
        elements.selectedId.textContent = selectedEdges.length > 1 ? `${edge.id} (+${selectedEdges.length - 1})` : edge.id;
        document.querySelectorAll('.class-button').forEach(button => {
            const uniform = currentClasses.size === 1 && currentClasses.has(button.dataset.class);
            button.classList.toggle('active-class', uniform);
            button.disabled = !layer.allowedClasses.includes(button.dataset.class);
        });
    }
    updateAdvancedUi();
}

function clearSelection() {
    state.selectedEdgeId = null;
    state.selectedEdgeIds.clear();
    state.primaryEdgeId = null;
    state.selectedVertices.clear();
    state.primaryVertexKey = null;
    state.moveMode = false;
    state.addPointMode = false;
    state.movePreview = null;
    updateSelectionUi();
    render();
}

function classLabel(layer, className) {
    if (layer.key === 'jma' && className === 'fine') return 'JMA reporting area';
    return CLASS_LABELS[className] || className;
}

function syncLayerControlLabels() {
    const layer = activeLayer();
    if (!layer) return;
    const warningButton = document.querySelector('.class-button[data-class="warning"]');
    const fineButton = document.querySelector('.class-button[data-class="fine"]');
    fineButton.textContent = layer.key === 'jma' ? 'JMA area' : 'Municipality';
    warningButton.classList.toggle('hidden', !layer.allowedClasses.includes('warning'));
    elements.toggleWarning.disabled = !layer.allowedClasses.includes('warning');
}

function updateLayerUi() {
    const layer = activeLayer();
    if (!layer) return;
    syncLayerControlLabels();
    state.selectedEdgeId = null;
    state.selectedEdgeIds.clear();
    state.primaryEdgeId = null;
    state.selectedVertices.clear();
    state.primaryVertexKey = null;
    state.moveMode = false;
    state.addPointMode = false;
    state.areaOperation = null;
    state.selectedAreaCodes.clear();
    state.hoverAreaCode = null;
    state.movePreview = null;
    state.searchHighlightCode = null;
    elements.searchInput.value = '';
    elements.searchResults.className = 'result-list empty';
    elements.searchResults.textContent = 'Search works in Japanese and English.';
    fitBounds(layer.bounds);
    updateSelectionUi();
    updateModifiedUi();
    updateTopologyIssueUi();
    updateAreaOperationUi();
    render();
}

function updateAdvancedUi() {
    const pointCount = state.selectedVertices.size;
    const vorticeCount = state.selectedEdgeIds.size;
    elements.moveButton.disabled = !state.advanced || pointCount < 1;
    elements.addPointButton.disabled = !state.advanced;
    elements.createEdgeButton.disabled = !state.advanced || pointCount !== 2;
    elements.combinePointsButton.disabled = !state.advanced || pointCount < 2;
    elements.combineButton.disabled = !state.advanced || vorticeCount < 2;
    elements.deleteButton.disabled = !state.advanced || pointCount < 1;
    elements.deleteEdgeButton.disabled = vorticeCount < 1;
    elements.restoreBaselineButton.disabled = !state.advanced;
    elements.moveButton.classList.toggle('active-mode', state.moveMode);
    elements.addPointButton.classList.toggle('active-mode', state.addPointMode);
    elements.advancedStatus.textContent = !state.advanced
        ? 'Geometry editing disabled.'
        : state.addPointMode
            ? 'Add point mode: click a vortice where the new point should be inserted.'
            : state.moveMode
                ? 'Move mode: drag any selected point. All selected points move together.'
                : `Geometry editing enabled. Points appear from ${VERTEX_VISIBLE_ZOOM}× editor zoom.`;
    elements.massWarningButton.classList.toggle('hidden', activeLayer()?.key === 'jma');
    const geometryMode = state.addPointMode ? 'ADD POINT — CLICK VORTICE' : state.moveMode ? 'MOVE SELECTED POINTS' : '';
    elements.modeChip.classList.toggle('hidden', !geometryMode);
    elements.modeChip.textContent = geometryMode;
    updateHistoryUi();
}

function resizeCanvas() {
    const rect = elements.canvas.getBoundingClientRect();
    elements.canvas.width = Math.max(1, Math.floor(rect.width));
    elements.canvas.height = Math.max(1, Math.floor(rect.height));
}

function fitBounds(bounds, padding = 28) {
    if (!bounds) return;
    const width = elements.canvas.width;
    const height = elements.canvas.height;
    const contentWidth = Math.max(1e-9, bounds.maxX - bounds.minX);
    const contentHeight = Math.max(1e-9, bounds.maxY - bounds.minY);
    const scale = Math.min((width - padding * 2) / contentWidth, (height - padding * 2) / contentHeight);
    state.viewport.fitScale = scale;
    state.viewport.scale = scale;
    state.viewport.offsetX = padding - bounds.minX * scale + (width - padding * 2 - contentWidth * scale) / 2;
    state.viewport.offsetY = padding - bounds.minY * scale + (height - padding * 2 - contentHeight * scale) / 2;
    updateZoomText();
}

function worldToScreen(x, y) {
    return { x: x * state.viewport.scale + state.viewport.offsetX, y: y * state.viewport.scale + state.viewport.offsetY };
}
function screenToWorld(x, y) {
    return { x: (x - state.viewport.offsetX) / state.viewport.scale, y: (y - state.viewport.offsetY) / state.viewport.scale };
}
function screenRectToWorld(left, top, right, bottom) {
    const a = screenToWorld(left, top);
    const b = screenToWorld(right, bottom);
    return { minX: Math.min(a.x, b.x), minY: Math.min(a.y, b.y), maxX: Math.max(a.x, b.x), maxY: Math.max(a.y, b.y) };
}

function findAreaAtScreen(clientX, clientY) {
    const layer = activeLayer();
    if (!layer) return null;
    const rect = elements.canvas.getBoundingClientRect();
    const world = screenToWorld(clientX - rect.left, clientY - rect.top);
    const indexes = areaIndexesAtWorldPoint(layer, world.x, world.y);
    if (!indexes.length) return null;
    indexes.sort((a, b) => {
        const ba = layer.areaBounds.get(layer.areas[a].code);
        const bb = layer.areaBounds.get(layer.areas[b].code);
        const aa = (ba.maxX - ba.minX) * (ba.maxY - ba.minY);
        const ab = (bb.maxX - bb.minX) * (bb.maxY - bb.minY);
        return aa - ab;
    });
    return layer.areas[indexes[0]];
}

function oceanSelectionBounds(layer) {
    const width = Math.max(1e-12, layer.bounds.maxX - layer.bounds.minX);
    const height = Math.max(1e-12, layer.bounds.maxY - layer.bounds.minY);
    return {
        minX: layer.bounds.minX - width * OCEAN_SELECTION_PADDING,
        maxX: layer.bounds.maxX + width * OCEAN_SELECTION_PADDING,
        minY: layer.bounds.minY - height * OCEAN_SELECTION_PADDING,
        maxY: layer.bounds.maxY + height * OCEAN_SELECTION_PADDING,
    };
}

function isInsideOceanSelectionBounds(layer, world) {
    const bounds = oceanSelectionBounds(layer);
    return world.x >= bounds.minX && world.x <= bounds.maxX && world.y >= bounds.minY && world.y <= bounds.maxY;
}

function findSelectableAreaAtScreen(clientX, clientY) {
    const layer = activeLayer();
    if (!layer) return null;
    const area = findAreaAtScreen(clientX, clientY);
    if (area) return area;
    if (state.areaOperation !== 'mass-coast') return null;
    const rect = elements.canvas.getBoundingClientRect();
    const world = screenToWorld(clientX - rect.left, clientY - rect.top);
    if (!isInsideOceanSelectionBounds(layer, world)) return null;
    return { code: OCEAN_CODE, name: 'Ocean', nameEn: 'Ocean', isOcean: true };
}

function appendAreaPath(ctx, layer, area) {
    for (const ring of area.rings) {
        if (!ring.length) continue;
        let projected = projectGeo(ring[0][1] / layer.quantization, ring[0][0] / layer.quantization);
        let screen = worldToScreen(projected.x, projected.y);
        ctx.moveTo(screen.x, screen.y);
        for (let index = 1; index < ring.length; index += 1) {
            projected = projectGeo(ring[index][1] / layer.quantization, ring[index][0] / layer.quantization);
            screen = worldToScreen(projected.x, projected.y);
            ctx.lineTo(screen.x, screen.y);
        }
        ctx.closePath();
    }
}

function traceAreaPath(ctx, layer, area) {
    ctx.beginPath();
    appendAreaPath(ctx, layer, area);
}

function traceOceanPath(ctx, layer) {
    const bounds = oceanSelectionBounds(layer);
    const topLeft = worldToScreen(bounds.minX, bounds.minY);
    const bottomRight = worldToScreen(bounds.maxX, bounds.maxY);
    ctx.beginPath();
    ctx.rect(topLeft.x, topLeft.y, bottomRight.x - topLeft.x, bottomRight.y - topLeft.y);
    for (const area of layer.areas) appendAreaPath(ctx, layer, area);
}

function renderAreaSelection(ctx, layer) {
    if (!state.areaOperation) return;
    for (const code of state.selectedAreaCodes) {
        if (code === OCEAN_CODE) {
            traceOceanPath(ctx, layer);
            ctx.fillStyle = CLASS_COLORS.areaSelected;
            ctx.fill('evenodd');
            continue;
        }
        const area = layer.areas.find(item => item.code === code);
        if (!area) continue;
        traceAreaPath(ctx, layer, area);
        ctx.fillStyle = CLASS_COLORS.areaSelected;
        ctx.fill('evenodd');
    }
    if (state.hoverAreaCode && !state.selectedAreaCodes.has(state.hoverAreaCode)) {
        if (state.hoverAreaCode === OCEAN_CODE) {
            traceOceanPath(ctx, layer);
            ctx.fillStyle = CLASS_COLORS.areaHover;
            ctx.fill('evenodd');
        } else {
            const area = layer.areas.find(item => item.code === state.hoverAreaCode);
            if (area) {
                traceAreaPath(ctx, layer, area);
                ctx.fillStyle = CLASS_COLORS.areaHover;
                ctx.fill('evenodd');
            }
        }
    }
}

function areaOperationPlan(layer) {
    const selected = state.selectedAreaCodes;
    const result = new Map();
    if (!selected.size || !state.areaOperation) return result;

    const assignWithPriority = (edge, targetClass) => {
        const currentPriority = CLASS_PRIORITY[edge.currentClass] ?? 0;
        const targetPriority = CLASS_PRIORITY[targetClass] ?? 0;
        if (targetPriority < currentPriority) return;
        if (edge.currentClass !== targetClass) result.set(edge.id, targetClass);
    };

    // Inner edges are intentionally normalized to Municipality for a selected
    // Warning-area group. Coast and Prefecture remain protected, but an old
    // Warning assignment must be cleared because it is no longer on the outer
    // perimeter of the selected group.
    const assignInnerMunicipality = edge => {
        if (edge.currentClass === 'coast' || edge.currentClass === 'prefecture') return;
        if (edge.currentClass !== 'fine') result.set(edge.id, 'fine');
    };

    if (state.areaOperation === 'mass-coast') {
        if (!selected.has(OCEAN_CODE)) return result;
        const selectedLand = new Set([...selected].filter(code => code !== OCEAN_CODE));
        for (const edge of layer.edges.values()) {
            if (edge.ownerAreas.length !== 1) continue;
            if (selectedLand.size && !selectedLand.has(edge.ownerAreas[0]?.code)) continue;
            assignWithPriority(edge, 'coast');
        }
    } else if (state.areaOperation === 'mass-warning') {
        for (const edge of layer.edges.values()) {
            const selectedOwners = edge.ownerAreas.filter(area => selected.has(area.code)).length;
            if (!selectedOwners) continue;
            if (edge.ownerAreas.length === 2 && selectedOwners === 2) assignInnerMunicipality(edge);
            else if (selectedOwners === 1) assignWithPriority(edge, 'warning');
        }
    } else if (state.areaOperation === 'mass-prefecture') {
        for (const edge of layer.edges.values()) {
            const selectedOwners = edge.ownerAreas.filter(area => selected.has(area.code)).length;
            if (!selectedOwners) continue;
            if (layer.key === 'jma' && edge.ownerAreas.length === 2 && selectedOwners === 2) assignWithPriority(edge, 'fine');
            else if (selectedOwners === 1) assignWithPriority(edge, 'prefecture');
        }
    }
    return result;
}

function renderAreaOperationPreview(ctx, layer) {
    if (!state.areaOperation) return;
    const plan = areaOperationPlan(layer);
    if (plan.size) {
        const byClass = new Map();
        for (const [edgeId, className] of plan.entries()) {
            if (!byClass.has(className)) byClass.set(className, []);
            byClass.get(className).push(edgeId);
        }
        for (const [className, ids] of byClass.entries()) {
            ctx.strokeStyle = CLASS_COLORS[className] || CLASS_COLORS.selected;
            ctx.lineWidth = 4.5;
            ctx.setLineDash([8, 5]);
            ctx.beginPath();
            for (const id of ids) {
                const edge = layer.edges.get(id);
                if (!edge) continue;
                const a = worldToScreen(edge.x1, edge.y1);
                const b = worldToScreen(edge.x2, edge.y2);
                ctx.moveTo(a.x, a.y);
                ctx.lineTo(b.x, b.y);
            }
            ctx.stroke();
        }
        ctx.setLineDash([]);
    }
    if (state.areaOperation === 'create-edge') {
        const vertices = [...state.selectedVertices].map(key => layer.vertices.get(key)).filter(Boolean);
        if (vertices.length === 2) {
            const a = worldToScreen(vertices[0].x, vertices[0].y);
            const b = worldToScreen(vertices[1].x, vertices[1].y);
            ctx.strokeStyle = '#ffffff';
            ctx.lineWidth = 3;
            ctx.setLineDash([7, 5]);
            ctx.beginPath();
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
            ctx.stroke();
            ctx.setLineDash([]);
        }
    }
}

function updateAreaOperationUi() {
    if (!elements.areaOperationRow) return;
    const mode = state.areaOperation;
    elements.areaOperationRow.classList.toggle('hidden', !mode);
    elements.mapArea.classList.toggle('area-pick-mode', Boolean(mode));
    elements.selectedAreaCount.textContent = state.selectedAreaCodes.has(OCEAN_CODE)
        ? `Ocean${state.selectedAreaCodes.size > 1 ? ` + ${state.selectedAreaCodes.size - 1}` : ''}`
        : String(state.selectedAreaCodes.size);
    if (!mode) return;
    const labels = {
        'reassign': ['Reassign areas', 'Click exactly two filled areas that should own the selected vortice(s).'],
        'mass-coast': ['Set coast from fill', 'Click the bounded Ocean fill. Confirm marks every one-owner shoreline as Coast. You may also select land areas to limit the operation.'],
        'mass-warning': ['Set warning zone from areas', 'Select any number of areas. Outer edges become Warning zone; inner edges become Municipality. Higher-priority Coast/Prefecture assignments are preserved.'],
        'mass-prefecture': ['Set prefecture from areas', activeLayer()?.key === 'jma'
            ? 'Select all JMA areas in the prefecture. Outer edges become Prefecture; inner edges become JMA area. Coast is preserved.'
            : 'Select any number of municipalities. Outer edges become Prefecture; inner edges are left unchanged. Coast is preserved.'],
        'create-edge': ['Create edge', 'Two geometry points are locked. Click exactly two filled owner areas, then Confirm.'],
    };
    const [label, help] = labels[mode] || ['Area operation', 'Click filled areas.'];
    elements.areaOperationLabel.textContent = label;
    elements.areaOperationHelp.textContent = help;
    const exactTwo = mode === 'reassign' || mode === 'create-edge';
    if (mode === 'mass-coast') {
        elements.areaOperationConfirm.disabled = !state.selectedAreaCodes.has(OCEAN_CODE);
    } else {
        elements.areaOperationConfirm.disabled = exactTwo ? state.selectedAreaCodes.size !== 2 : state.selectedAreaCodes.size < 1;
    }
}

function startAreaOperation(mode) {
    const layer = activeLayer();
    if (!layer) return;
    if (mode === 'reassign' && !state.selectedEdgeIds.size) {
        setStatus('Select one or more vortices first.');
        return;
    }
    if (mode === 'mass-warning' && layer.key === 'jma') return;
    if (mode === 'create-edge') {
        if (!state.advanced || state.selectedVertices.size !== 2) {
            setStatus('Create edge requires exactly two selected geometry points.');
            return;
        }
    }
    state.areaOperation = mode;
    state.selectedAreaCodes.clear();
    state.hoverAreaCode = null;
    state.moveMode = false;
    state.addPointMode = false;
    state.movePreview = null;
    updateAdvancedUi();
    updateAreaOperationUi();
    render();
}

function cancelAreaOperation() {
    state.areaOperation = null;
    state.selectedAreaCodes.clear();
    state.hoverAreaCode = null;
    updateAreaOperationUi();
    render();
}

function toggleAreaSelection(area) {
    if (!state.areaOperation || !area) return;
    const exactTwo = state.areaOperation === 'reassign' || state.areaOperation === 'create-edge';
    if (state.selectedAreaCodes.has(area.code)) {
        state.selectedAreaCodes.delete(area.code);
    } else {
        if (exactTwo && state.selectedAreaCodes.size >= 2) {
            setStatus('This operation needs exactly two owner areas. Deselect one first.');
            return;
        }
        state.selectedAreaCodes.add(area.code);
    }
    updateAreaOperationUi();
    render();
}

function reassignSelectedEdgeOwners() {
    const layer = activeLayer();
    if (!layer || state.selectedAreaCodes.size !== 2 || !state.selectedEdgeIds.size) return false;
    recordHistory('reassign edge areas');
    const ownerCodes = [...state.selectedAreaCodes];
    layer.ownerOverrideById ??= new Map();
    layer.manualEdges ??= new Map();
    for (const edgeId of state.selectedEdgeIds) {
        layer.ownerOverrideById.set(edgeId, [...ownerCodes]);
        const manual = layer.manualEdges.get(edgeId);
        if (manual) manual.owners = [...ownerCodes];
    }
    layer.topologyRevision = (layer.topologyRevision || 0) + 1;
    const previousEdges = layer.edges;
    buildLayerModel(layer, previousEdges);
    state.selectedEdgeIds = new Set([...state.selectedEdgeIds].filter(id => layer.edges.has(id)));
    state.primaryEdgeId = layer.edges.has(state.primaryEdgeId) ? state.primaryEdgeId : [...state.selectedEdgeIds].at(-1) || null;
    state.selectedEdgeId = state.primaryEdgeId;
    updateModifiedUi();
    updateSelectionUi();
    updateTopologyIssueUi();
    setStatus(`Reassigned ${state.selectedEdgeIds.size} vortice${state.selectedEdgeIds.size === 1 ? '' : 's'} to the two selected areas.${topologyIssueSuffix(layer)}`);
    return true;
}

function applyMassAreaClassification() {
    const layer = activeLayer();
    if (!layer || !state.selectedAreaCodes.size) return false;
    const plan = areaOperationPlan(layer);
    if (!plan.size) {
        setStatus('The selected areas do not produce any boundary changes.');
        return false;
    }
    const historyLabel = state.areaOperation === 'mass-coast'
        ? 'set coast from fill'
        : state.areaOperation === 'mass-warning' ? 'set warning zone from areas' : 'set prefecture from areas';
    recordHistory(historyLabel);
    if (state.areaOperation === 'mass-coast' && state.selectedAreaCodes.has(OCEAN_CODE)
        && [...state.selectedAreaCodes].every(code => code === OCEAN_CODE)) {
        layer.coastAllExterior = true;
    }
    let changed = 0;
    for (const [edgeId, className] of plan.entries()) {
        const edge = layer.edges.get(edgeId);
        if (!edge || !layer.allowedClasses.includes(className) || edge.currentClass === className) continue;
        edge.currentClass = className;
        changed += 1;
    }
    refreshTopologyIssues(layer);
    updateModifiedUi();
    updateSelectionUi();
    updateTopologyIssueUi();
    const selectedCount = [...state.selectedAreaCodes].filter(code => code !== OCEAN_CODE).length;
    const scope = state.areaOperation === 'mass-coast'
        ? (selectedCount ? `Ocean + ${selectedCount} selected land area${selectedCount === 1 ? '' : 's'}` : 'the bounded Ocean fill')
        : `${state.selectedAreaCodes.size} selected area${state.selectedAreaCodes.size === 1 ? '' : 's'}`;
    setStatus(`Updated ${changed} boundary vortice${changed === 1 ? '' : 's'} from ${scope}.${topologyIssueSuffix(layer)}`);
    return true;
}

function createManualEdgeFromSelectedPoints() {
    const layer = activeLayer();
    if (!layer || state.selectedVertices.size !== 2 || state.selectedAreaCodes.size !== 2) return false;
    const vertices = [...state.selectedVertices].map(key => layer.vertices.get(key)).filter(Boolean);
    if (vertices.length !== 2) return false;
    const points = vertices.map(vertex => [vertex.point[0], vertex.point[1]]);
    const id = edgeIdFromPoints(points[0], points[1]);
    if (layer.edges.has(id)) {
        setStatus('Create edge blocked: an edge already exists between those two points.');
        return false;
    }
    recordHistory('create edge');
    layer.manualEdges ??= new Map();
    layer.manualEdges.set(id, {
        id,
        points: points.map(point => [point[0], point[1]]),
        owners: [...state.selectedAreaCodes],
        class: 'fine',
        baselineClass: 'none',
    });
    layer.topologyRevision = (layer.topologyRevision || 0) + 1;
    const previousEdges = layer.edges;
    buildLayerModel(layer, previousEdges);
    const created = layer.edges.get(id);
    if (!created) {
        setStatus('Create edge failed: the new edge could not be rebuilt.');
        return false;
    }
    state.selectedEdgeIds = new Set([id]);
    state.primaryEdgeId = id;
    state.selectedEdgeId = id;
    updateModifiedUi();
    updateSelectionUi();
    updateTopologyIssueUi();
    setStatus(`Created a new two-owner edge. It starts as ${classLabel(layer, 'fine')}; reclassify it if needed.${topologyIssueSuffix(layer)}`);
    return true;
}

function confirmAreaOperation() {
    const mode = state.areaOperation;
    if (!mode) return;
    let completed = false;
    if (mode === 'reassign') completed = reassignSelectedEdgeOwners();
    else if (mode === 'create-edge') completed = createManualEdgeFromSelectedPoints();
    else completed = applyMassAreaClassification();
    if (completed) cancelAreaOperation();
}

function visibleVertexKeys(layer) {
    if (!state.advanced || !state.show.vertices || zoomRatio() < VERTEX_VISIBLE_ZOOM) return [];
    const world = screenRectToWorld(0, 0, elements.canvas.width, elements.canvas.height);
    const minGX = Math.floor(world.minX / VERTEX_GRID_SIZE) - 1;
    const maxGX = Math.floor(world.maxX / VERTEX_GRID_SIZE) + 1;
    const minGY = Math.floor(world.minY / VERTEX_GRID_SIZE) - 1;
    const maxGY = Math.floor(world.maxY / VERTEX_GRID_SIZE) + 1;
    const result = [];
    const seen = new Set();
    for (let gx = minGX; gx <= maxGX; gx += 1) {
        for (let gy = minGY; gy <= maxGY; gy += 1) {
            for (const key of layer.vertexSpatialIndex.get(`${gx},${gy}`) || []) {
                if (seen.has(key)) continue;
                seen.add(key);
                const vertex = layer.vertices.get(key);
                if (vertex.x >= world.minX && vertex.x <= world.maxX && vertex.y >= world.minY && vertex.y <= world.maxY) result.push(key);
            }
        }
    }
    return result;
}

function longitudeToWorldX(longitude) {
    return longitude * Math.PI / 180;
}

function latitudeToWorldY(latitude) {
    return projectGeo(latitude, 0).y;
}

function slippyZoomForViewport() {
    const ideal = Math.log2(Math.max(1e-9, state.viewport.scale * Math.PI * 2 / BASEMAP.tileSize));
    return Math.max(BASEMAP.minZoom, Math.min(BASEMAP.maxZoom, Math.round(ideal)));
}

function worldToTileX(worldX, zoom) {
    const count = 2 ** zoom;
    return (worldX + Math.PI) / (Math.PI * 2) * count;
}

function worldToTileY(worldY, zoom) {
    const count = 2 ** zoom;
    return (worldY + Math.PI) / (Math.PI * 2) * count;
}

function requestBasemapTile(zoom, x, y) {
    const count = 2 ** zoom;
    if (x < 0 || y < 0 || x >= count || y >= count) return null;
    const key = `${zoom}/${x}/${y}`;
    let tile = state.basemapTiles.get(key);
    if (tile) return tile;
    const image = new Image();
    tile = { image, loaded: false, failed: false };
    state.basemapTiles.set(key, tile);
    image.addEventListener('load', () => {
        tile.loaded = true;
        if (state.show.basemap) render();
    }, { once: true });
    image.addEventListener('error', () => { tile.failed = true; }, { once: true });
    image.src = BASEMAP.tileUrl(zoom, x, y);
    return tile;
}

function renderBasemap(ctx) {
    if (!state.show.basemap) return;
    const viewport = screenRectToWorld(0, 0, elements.canvas.width, elements.canvas.height);
    const japan = {
        minX: longitudeToWorldX(BASEMAP.west),
        maxX: longitudeToWorldX(BASEMAP.east),
        minY: latitudeToWorldY(BASEMAP.north),
        maxY: latitudeToWorldY(BASEMAP.south),
    };
    const visible = {
        minX: Math.max(viewport.minX, japan.minX),
        maxX: Math.min(viewport.maxX, japan.maxX),
        minY: Math.max(viewport.minY, japan.minY),
        maxY: Math.min(viewport.maxY, japan.maxY),
    };
    if (visible.minX >= visible.maxX || visible.minY >= visible.maxY) return;

    const zoom = slippyZoomForViewport();
    const count = 2 ** zoom;
    const minTileX = Math.max(0, Math.floor(worldToTileX(visible.minX, zoom)));
    const maxTileX = Math.min(count - 1, Math.floor(worldToTileX(visible.maxX, zoom)));
    const minTileY = Math.max(0, Math.floor(worldToTileY(visible.minY, zoom)));
    const maxTileY = Math.min(count - 1, Math.floor(worldToTileY(visible.maxY, zoom)));

    ctx.save();
    ctx.globalAlpha = BASEMAP.opacity;
    for (let tileY = minTileY; tileY <= maxTileY; tileY += 1) {
        for (let tileX = minTileX; tileX <= maxTileX; tileX += 1) {
            const tile = requestBasemapTile(zoom, tileX, tileY);
            if (!tile?.loaded || tile.failed) continue;
            const worldLeft = -Math.PI + (Math.PI * 2 * tileX / count);
            const worldTop = -Math.PI + (Math.PI * 2 * tileY / count);
            const worldRight = -Math.PI + (Math.PI * 2 * (tileX + 1) / count);
            const worldBottom = -Math.PI + (Math.PI * 2 * (tileY + 1) / count);
            const topLeft = worldToScreen(worldLeft, worldTop);
            const bottomRight = worldToScreen(worldRight, worldBottom);
            ctx.drawImage(tile.image, Math.floor(topLeft.x), Math.floor(topLeft.y), Math.ceil(bottomRight.x - topLeft.x) + 1, Math.ceil(bottomRight.y - topLeft.y) + 1);
        }
    }
    ctx.restore();
}

function render() {
    const layer = activeLayer();
    const ctx = elements.canvas.getContext('2d');
    const width = elements.canvas.width;
    const height = elements.canvas.height;
    ctx.clearRect(0, 0, width, height);
    ctx.fillStyle = '#09111c';
    ctx.fillRect(0, 0, width, height);
    if (!layer) return;

    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    renderAreaSelection(ctx, layer);

    renderBasemap(ctx);

    for (const className of ['fine', 'warning', 'prefecture', 'coast']) {
        const visible = className === 'coast' ? state.show.coast : state.show[className];
        if (!visible || !layer.allowedClasses.includes(className)) continue;
        ctx.strokeStyle = CLASS_COLORS[className];
        ctx.lineWidth = className === 'fine' ? 0.95 : className === 'warning' ? 1.9 : className === 'prefecture' ? 2.8 : 2.3;
        ctx.beginPath();
        for (const edge of layer.edges.values()) {
            if (edge.currentClass !== className) continue;
            const a = worldToScreen(edge.x1, edge.y1);
            const b = worldToScreen(edge.x2, edge.y2);
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
        }
        ctx.stroke();
    }

    if (state.show.modified) {
        ctx.strokeStyle = 'rgba(255,107,107,0.92)';
        ctx.lineWidth = 3.3;
        ctx.beginPath();
        for (const edge of layer.edges.values()) {
            if (edge.currentClass === edge.baselineClass) continue;
            const a = worldToScreen(edge.x1, edge.y1);
            const b = worldToScreen(edge.x2, edge.y2);
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
        }
        ctx.stroke();
    }

    if (state.show.errors && layer.topologyIssues?.length) {
        ctx.strokeStyle = CLASS_COLORS.topologyError;
        ctx.lineWidth = 5.2;
        ctx.beginPath();
        for (const issue of layer.topologyIssues) {
            const edge = layer.edges.get(issue.edgeId);
            if (!edge) continue;
            const a = worldToScreen(edge.x1, edge.y1);
            const b = worldToScreen(edge.x2, edge.y2);
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
        }
        ctx.stroke();
    }

    renderAreaOperationPreview(ctx, layer);

    if (state.searchHighlightCode) renderSearchHighlight(ctx, layer, state.searchHighlightCode);

    if (state.selectedEdgeIds.size) {
        ctx.strokeStyle = CLASS_COLORS.selected;
        ctx.lineWidth = 5;
        ctx.beginPath();
        for (const id of state.selectedEdgeIds) {
            const selectedEdge = layer.edges.get(id);
            if (!selectedEdge) continue;
            const a = worldToScreen(selectedEdge.x1, selectedEdge.y1);
            const b = worldToScreen(selectedEdge.x2, selectedEdge.y2);
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
        }
        ctx.stroke();
    }

    if (state.advanced && state.show.vertices && zoomRatio() >= VERTEX_VISIBLE_ZOOM) {
        const keys = visibleVertexKeys(layer);
        ctx.fillStyle = 'rgba(192,200,214,0.78)';
        for (const key of keys) {
            if (state.selectedVertices.has(key)) continue;
            const vertex = layer.vertices.get(key);
            const point = worldToScreen(vertex.x, vertex.y);
            ctx.beginPath();
            ctx.arc(point.x, point.y, 2.2, 0, Math.PI * 2);
            ctx.fill();
        }
    }

    if (state.advanced && state.show.vertices) {
        for (const key of state.selectedVertices) {
            const vertex = layer.vertices.get(key);
            if (!vertex) continue;
            const point = worldToScreen(vertex.x, vertex.y);
            ctx.fillStyle = CLASS_COLORS.selectedVertex;
            ctx.strokeStyle = key === state.primaryVertexKey ? CLASS_COLORS.primaryVertex : '#552d00';
            ctx.lineWidth = key === state.primaryVertexKey ? 2.3 : 1.2;
            ctx.beginPath();
            ctx.arc(point.x, point.y, key === state.primaryVertexKey ? 5.2 : 4.2, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();
        }
    }

    if (state.movePreview) {
        ctx.strokeStyle = 'rgba(255,159,67,0.65)';
        ctx.fillStyle = 'rgba(255,159,67,0.9)';
        ctx.lineWidth = 1.3;
        for (const [oldKey, newPoint] of state.movePreview.mapping.entries()) {
            const vertex = layer.vertices.get(oldKey);
            if (!vertex) continue;
            const from = worldToScreen(vertex.x, vertex.y);
            const projected = projectGeo(newPoint[1] / layer.quantization, newPoint[0] / layer.quantization);
            const to = worldToScreen(projected.x, projected.y);
            ctx.beginPath();
            ctx.moveTo(from.x, from.y);
            ctx.lineTo(to.x, to.y);
            ctx.stroke();
            ctx.beginPath();
            ctx.arc(to.x, to.y, 4.2, 0, Math.PI * 2);
            ctx.fill();
        }
    }

    if (state.selectionBox) {
        const box = state.selectionBox;
        const left = Math.min(box.startX, box.currentX);
        const top = Math.min(box.startY, box.currentY);
        const right = Math.max(box.startX, box.currentX);
        const bottom = Math.max(box.startY, box.currentY);
        ctx.fillStyle = 'rgba(123,183,255,0.10)';
        ctx.strokeStyle = 'rgba(123,183,255,0.9)';
        ctx.lineWidth = 1;
        ctx.fillRect(left, top, right - left, bottom - top);
        ctx.strokeRect(left, top, right - left, bottom - top);
    }
}

function renderSearchHighlight(ctx, layer, code) {
    const areaIndex = layer.areas.findIndex(area => area.code === code);
    if (areaIndex < 0) return;
    const area = layer.areas[areaIndex];
    ctx.strokeStyle = CLASS_COLORS.search;
    ctx.lineWidth = 4;
    ctx.beginPath();
    for (const ring of area.rings) {
        if (!ring.length) continue;
        const first = projectGeo(ring[0][1] / layer.quantization, ring[0][0] / layer.quantization);
        let screen = worldToScreen(first.x, first.y);
        ctx.moveTo(screen.x, screen.y);
        for (let index = 1; index < ring.length; index += 1) {
            const projected = projectGeo(ring[index][1] / layer.quantization, ring[index][0] / layer.quantization);
            screen = worldToScreen(projected.x, projected.y);
            ctx.lineTo(screen.x, screen.y);
        }
        ctx.closePath();
    }
    ctx.stroke();
    if (state.show.labels) {
        const bounds = layer.areaBounds.get(code);
        if (bounds) {
            const center = worldToScreen((bounds.minX + bounds.maxX) / 2, (bounds.minY + bounds.maxY) / 2);
            ctx.fillStyle = '#d2f5ca';
            ctx.font = '13px Segoe UI';
            const label = area.nameEn && area.nameEn !== area.name ? `${area.nameEn} (${area.name})` : area.name;
            ctx.fillText(label, center.x + 7, center.y - 7);
        }
    }
}

function findVertexAtScreen(clientX, clientY) {
    const layer = activeLayer();
    if (!layer || !state.advanced || !state.show.vertices || zoomRatio() < VERTEX_HIT_ZOOM) return null;
    const rect = elements.canvas.getBoundingClientRect();
    const sx = clientX - rect.left;
    const sy = clientY - rect.top;
    const world = screenToWorld(sx, sy);
    const gx = Math.floor(world.x / VERTEX_GRID_SIZE);
    const gy = Math.floor(world.y / VERTEX_GRID_SIZE);
    let best = null;
    let bestDistance = 9 * 9;
    for (let dx = -1; dx <= 1; dx += 1) {
        for (let dy = -1; dy <= 1; dy += 1) {
            for (const key of layer.vertexSpatialIndex.get(`${gx + dx},${gy + dy}`) || []) {
                const vertex = layer.vertices.get(key);
                const screen = worldToScreen(vertex.x, vertex.y);
                const distance = (screen.x - sx) ** 2 + (screen.y - sy) ** 2;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = vertex;
                }
            }
        }
    }
    return best;
}

function findEdgeAtScreen(clientX, clientY) {
    const layer = activeLayer();
    if (!layer) return null;
    const rect = elements.canvas.getBoundingClientRect();
    const sx = clientX - rect.left;
    const sy = clientY - rect.top;
    const world = screenToWorld(sx, sy);
    const gx = Math.floor(world.x / GRID_SIZE);
    const gy = Math.floor(world.y / GRID_SIZE);
    const tolerance = 8 / state.viewport.scale;
    let best = null;
    let bestDistance = tolerance * tolerance;
    const seen = new Set();
    for (let dx = -1; dx <= 1; dx += 1) {
        for (let dy = -1; dy <= 1; dy += 1) {
            for (const id of layer.edgeSpatialIndex.get(`${gx + dx},${gy + dy}`) || []) {
                if (seen.has(id)) continue;
                seen.add(id);
                const edge = layer.edges.get(id);
                const visibleDeleted = edge?.currentClass === 'none' && state.show.modified && edge.currentClass !== edge.baselineClass;
                if (!edge || (!state.show[edge.currentClass] && !visibleDeleted)) continue;
                const distance = distanceSquaredToSegment(world, edge);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = edge;
                }
            }
        }
    }
    return best;
}

function setSelectedEdge(edge) {
    state.selectedEdgeIds = edge ? new Set([edge.id]) : new Set();
    state.primaryEdgeId = edge?.id || null;
    state.selectedEdgeId = edge?.id || null;
    updateSelectionUi();
    render();
}

function selectEdge(edge, event = {}) {
    const layer = activeLayer();
    if (!edge || !layer) return;
    if (event.ctrlKey && state.primaryEdgeId && state.primaryEdgeId !== edge.id) {
        setStatus('Finding directly connected vortice chain…');
        const path = shortestEdgePath(layer, state.primaryEdgeId, edge.id);
        if (path.length) {
            state.selectedEdgeIds = new Set(path);
            state.primaryEdgeId = edge.id;
            state.selectedEdgeId = edge.id;
            setStatus(`Selected ${path.length} directly connected vortices.`);
        } else {
            setStatus('No directly connected vortice path found.');
        }
    } else if (event.shiftKey) {
        if (state.selectedEdgeIds.has(edge.id)) {
            state.selectedEdgeIds.delete(edge.id);
            if (state.primaryEdgeId === edge.id) state.primaryEdgeId = [...state.selectedEdgeIds].at(-1) || null;
        } else {
            state.selectedEdgeIds.add(edge.id);
            state.primaryEdgeId = edge.id;
        }
        state.selectedEdgeId = state.primaryEdgeId;
    } else {
        state.selectedEdgeIds = new Set([edge.id]);
        state.primaryEdgeId = edge.id;
        state.selectedEdgeId = edge.id;
    }
    updateSelectionUi();
    render();
}

function selectVertex(vertex, event) {
    const layer = activeLayer();
    if (!vertex || !layer || !state.advanced) return;
    if (event.shiftKey) {
        if (state.selectedVertices.has(vertex.key)) {
            state.selectedVertices.delete(vertex.key);
            if (state.primaryVertexKey === vertex.key) state.primaryVertexKey = [...state.selectedVertices].at(-1) || null;
        } else {
            state.selectedVertices.add(vertex.key);
            state.primaryVertexKey = vertex.key;
        }
    } else {
        state.selectedVertices = new Set([vertex.key]);
        state.primaryVertexKey = vertex.key;
    }
    updateSelectionUi();
    render();
}

function edgeIntersectsRect(edge, rect) {
    if (edge.maxX < rect.minX || edge.minX > rect.maxX || edge.maxY < rect.minY || edge.minY > rect.maxY) return false;
    const inside = (x, y) => x >= rect.minX && x <= rect.maxX && y >= rect.minY && y <= rect.maxY;
    if (inside(edge.x1, edge.y1) || inside(edge.x2, edge.y2)) return true;
    const intersects = (ax, ay, bx, by, cx, cy, dx, dy) => {
        const cross = (px, py, qx, qy, rx, ry) => (qx - px) * (ry - py) - (qy - py) * (rx - px);
        const d1 = cross(ax, ay, bx, by, cx, cy);
        const d2 = cross(ax, ay, bx, by, dx, dy);
        const d3 = cross(cx, cy, dx, dy, ax, ay);
        const d4 = cross(cx, cy, dx, dy, bx, by);
        return ((d1 === 0 || d2 === 0 || (d1 < 0) !== (d2 < 0)) && (d3 === 0 || d4 === 0 || (d3 < 0) !== (d4 < 0)));
    };
    return intersects(edge.x1, edge.y1, edge.x2, edge.y2, rect.minX, rect.minY, rect.maxX, rect.minY)
        || intersects(edge.x1, edge.y1, edge.x2, edge.y2, rect.maxX, rect.minY, rect.maxX, rect.maxY)
        || intersects(edge.x1, edge.y1, edge.x2, edge.y2, rect.maxX, rect.maxY, rect.minX, rect.maxY)
        || intersects(edge.x1, edge.y1, edge.x2, edge.y2, rect.minX, rect.maxY, rect.minX, rect.minY);
}

function selectEdgesInBox(box) {
    const layer = activeLayer();
    if (!layer) return;
    const world = screenRectToWorld(
        Math.min(box.startX, box.currentX),
        Math.min(box.startY, box.currentY),
        Math.max(box.startX, box.currentX),
        Math.max(box.startY, box.currentY),
    );
    const selected = new Set(state.selectedEdgeIds);
    const minGX = Math.floor(world.minX / GRID_SIZE) - 1;
    const maxGX = Math.floor(world.maxX / GRID_SIZE) + 1;
    const minGY = Math.floor(world.minY / GRID_SIZE) - 1;
    const maxGY = Math.floor(world.maxY / GRID_SIZE) + 1;
    const seen = new Set();
    for (let gx = minGX; gx <= maxGX; gx += 1) {
        for (let gy = minGY; gy <= maxGY; gy += 1) {
            for (const id of layer.edgeSpatialIndex.get(`${gx},${gy}`) || []) {
                if (seen.has(id)) continue;
                seen.add(id);
                const edge = layer.edges.get(id);
                if (edge && edgeIntersectsRect(edge, world)) selected.add(id);
            }
        }
    }
    state.selectedEdgeIds = selected;
    state.primaryEdgeId = [...selected].at(-1) || state.primaryEdgeId;
    state.selectedEdgeId = state.primaryEdgeId;
    setStatus(`Selected ${selected.size} vortices.`);
    updateSelectionUi();
    render();
}

function selectVerticesInBox(box) {
    const layer = activeLayer();
    if (!layer || !state.advanced || zoomRatio() < VERTEX_HIT_ZOOM) return;
    const world = screenRectToWorld(
        Math.min(box.startX, box.currentX),
        Math.min(box.startY, box.currentY),
        Math.max(box.startX, box.currentX),
        Math.max(box.startY, box.currentY),
    );
    const selected = new Set(state.selectedVertices);
    const minGX = Math.floor(world.minX / VERTEX_GRID_SIZE) - 1;
    const maxGX = Math.floor(world.maxX / VERTEX_GRID_SIZE) + 1;
    const minGY = Math.floor(world.minY / VERTEX_GRID_SIZE) - 1;
    const maxGY = Math.floor(world.maxY / VERTEX_GRID_SIZE) + 1;
    for (let gx = minGX; gx <= maxGX; gx += 1) {
        for (let gy = minGY; gy <= maxGY; gy += 1) {
            for (const key of layer.vertexSpatialIndex.get(`${gx},${gy}`) || []) {
                const vertex = layer.vertices.get(key);
                if (vertex.x >= world.minX && vertex.x <= world.maxX && vertex.y >= world.minY && vertex.y <= world.maxY) selected.add(key);
            }
        }
    }
    state.selectedVertices = selected;
    state.primaryVertexKey = [...selected].at(-1) || state.primaryVertexKey;
    setStatus(`Selected ${selected.size} geometry points.`);
    updateSelectionUi();
    render();
}

function applyClassToSelected(className) {
    const layer = activeLayer();
    if (!layer || !layer.allowedClasses.includes(className) || !state.selectedEdgeIds.size) return;
    const willChange = [...state.selectedEdgeIds].some(id => layer.edges.get(id)?.currentClass !== className);
    if (willChange) recordHistory('boundary classification');
    let changed = 0;
    for (const id of state.selectedEdgeIds) {
        const edge = layer.edges.get(id);
        if (!edge) continue;
        if (edge.currentClass !== className) {
            edge.currentClass = className;
            changed += 1;
        }
    }
    refreshTopologyIssues(layer);
    setStatus(changed ? `Reclassified ${changed} selected vortice${changed === 1 ? '' : 's'}.${topologyIssueSuffix(layer)}` : 'Selected vortices already use that class.');
    updateModifiedUi();
    updateSelectionUi();
    render();
}

function revertSelectedBoundary() {
    const layer = activeLayer();
    if (!layer || !state.selectedEdgeIds.size) return;
    const willChange = [...state.selectedEdgeIds].some(id => {
        const edge = layer.edges.get(id);
        return edge && edge.currentClass !== edge.baselineClass;
    });
    if (willChange) recordHistory('revert boundary');
    for (const id of state.selectedEdgeIds) {
        const edge = layer.edges.get(id);
        if (edge) edge.currentClass = edge.baselineClass;
    }
    refreshTopologyIssues(layer);
    updateTopologyIssueUi();
    updateModifiedUi();
    updateSelectionUi();
    render();
}

function focusBounds(bounds, targetZoom = 18) {
    if (!bounds) return;
    const width = elements.canvas.width;
    const height = elements.canvas.height;
    const contentWidth = Math.max(1e-8, bounds.maxX - bounds.minX);
    const contentHeight = Math.max(1e-8, bounds.maxY - bounds.minY);
    const fit = Math.min(width * 0.7 / contentWidth, height * 0.7 / contentHeight);
    const scale = Math.max(state.viewport.fitScale * targetZoom, Math.min(fit, state.viewport.fitScale * 100_000));
    const centerX = (bounds.minX + bounds.maxX) / 2;
    const centerY = (bounds.minY + bounds.maxY) / 2;
    state.viewport.scale = scale;
    state.viewport.offsetX = width / 2 - centerX * scale;
    state.viewport.offsetY = height / 2 - centerY * scale;
    updateZoomText();
    render();
}

function focusSelectedEdge() {
    const layer = activeLayer();
    const selected = [...state.selectedEdgeIds].map(id => layer?.edges.get(id)).filter(Boolean);
    if (!selected.length) return;
    const padding = 0.0005;
    focusBounds({
        minX: Math.min(...selected.map(edge => edge.minX)) - padding,
        minY: Math.min(...selected.map(edge => edge.minY)) - padding,
        maxX: Math.max(...selected.map(edge => edge.maxX)) + padding,
        maxY: Math.max(...selected.map(edge => edge.maxY)) + padding,
    }, 80);
}

function performSearch() {
    const layer = activeLayer();
    const query = elements.searchInput.value.trim().toLowerCase();
    const container = elements.searchResults;
    state.searchHighlightCode = null;
    if (!query) {
        container.className = 'result-list empty';
        container.textContent = 'Search works in Japanese and English.';
        render();
        return;
    }
    const results = layer.areas.filter(area =>
        area.code.toLowerCase().includes(query) ||
        area.name.toLowerCase().includes(query) ||
        (area.nameEn || '').toLowerCase().includes(query)
    ).slice(0, 80);
    container.innerHTML = '';
    container.className = results.length ? 'result-list' : 'result-list empty';
    if (!results.length) {
        container.textContent = 'No matching area.';
        render();
        return;
    }
    for (const area of results) {
        const item = document.createElement('div');
        item.className = 'result-item';
        const english = area.nameEn && area.nameEn !== area.name ? area.nameEn : '';
        item.innerHTML = `<strong>${english || area.name}</strong><small>${english ? `${area.name} • ` : ''}${area.code}</small>`;
        item.addEventListener('click', () => {
            state.searchHighlightCode = area.code;
            focusBounds(layer.areaBounds.get(area.code), 10);
            render();
        });
        container.appendChild(item);
    }
    render();
}

function previewMove(clientX, clientY) {
    const layer = activeLayer();
    if (!state.movePreview || !layer) return;
    const rect = elements.canvas.getBoundingClientRect();
    const currentWorld = screenToWorld(clientX - rect.left, clientY - rect.top);
    const dx = currentWorld.x - state.movePreview.startWorld.x;
    const dy = currentWorld.y - state.movePreview.startWorld.y;
    const mapping = new Map();
    for (const key of state.selectedVertices) {
        const vertex = layer.vertices.get(key);
        if (!vertex) continue;
        const geo = inverseProject(vertex.x + dx, vertex.y + dy);
        mapping.set(key, [
            Math.round(geo.longitude * layer.quantization),
            Math.round(geo.latitude * layer.quantization),
        ]);
    }
    state.movePreview.mapping = mapping;
    render();
}

function applyPointMapping(mapping, newPrimary = null, historyLabel = 'point edit') {
    const layer = activeLayer();
    if (!layer || !mapping.size) return false;
    const previousEdges = layer.edges;
    const affectedCodes = new Set();
    const proposed = layer.areas.map(area => {
        let changed = false;
        const rings = area.rings.map(ring => {
            const moved = ring.map(point => {
                const replacement = mapping.get(canonicalPointKey(point));
                if (replacement) changed = true;
                return replacement || point;
            });
            return normalizeRing(moved);
        });
        if (changed) affectedCodes.add(area.code);
        return { ...area, rings };
    });
    const invalid = proposed.find(area => area.rings.some(ring => ring.length < 3));
    if (invalid) {
        setStatus(`Geometry change blocked: ${invalid.name} would contain an invalid polygon ring.`);
        return false;
    }
    ensureSessionBaseForCodes(layer, affectedCodes);
    if (historyLabel) recordHistory(historyLabel);
    layer.areas = proposed;
    for (const code of affectedCodes) layer.changedAreaCodes.add(code);
    layer.geometryRevision += 1;
    buildLayerModel(layer, previousEdges);
    const translatedSelection = new Set();
    for (const key of state.selectedVertices) {
        const point = mapping.get(key);
        const translated = point ? canonicalPointKey(point) : key;
        if (layer.vertices.has(translated)) translatedSelection.add(translated);
    }
    state.selectedVertices = translatedSelection;
    state.primaryVertexKey = newPrimary || (state.primaryVertexKey && mapping.has(state.primaryVertexKey)
        ? canonicalPointKey(mapping.get(state.primaryVertexKey))
        : state.primaryVertexKey);
    if (!layer.vertices.has(state.primaryVertexKey)) state.primaryVertexKey = [...translatedSelection].at(-1) || null;
    state.selectedEdgeId = null;
    state.selectedEdgeIds.clear();
    state.primaryEdgeId = null;
    updateModifiedUi();
    updateSelectionUi();
    render();
    return true;
}

function combineSelectedPoints() {
    const layer = activeLayer();
    if (!state.advanced || !layer || state.selectedVertices.size < 2) return;
    const selectedKeys = [...state.selectedVertices].filter(key => layer.vertices.has(key));
    if (selectedKeys.length < 2) return;

    const primaryKey = state.primaryVertexKey && state.selectedVertices.has(state.primaryVertexKey)
        ? state.primaryVertexKey
        : selectedKeys[0];
    const primary = layer.vertices.get(primaryKey);
    if (!primary) return;

    if (!window.confirm(`Combine ${selectedKeys.length} selected points into the primary point? Every polygon using those points will be updated.`)) return;

    const mapping = new Map();
    for (const key of selectedKeys) {
        if (key !== primaryKey) mapping.set(key, [primary.point[0], primary.point[1]]);
    }
    if (!mapping.size) return;

    if (applyPointMapping(mapping, primaryKey, 'combine points')) {
        state.selectedVertices = new Set([primaryKey]);
        state.primaryVertexKey = primaryKey;
        updateSelectionUi();
        setStatus(`Combined ${selectedKeys.length} selected points into the primary point.${topologyIssueSuffix(layer)}`);
    }
}

function combineSelectedVortices() {
    const layer = activeLayer();
    if (!state.advanced || !layer || state.selectedEdgeIds.size < 2) return;
    const selectedIds = new Set(state.selectedEdgeIds);
    const selectedEdges = [...selectedIds].map(id => layer.edges.get(id)).filter(Boolean);
    if (selectedEdges.length !== selectedIds.size) return;

    const classSet = new Set(selectedEdges.map(edge => edge.currentClass));
    if (classSet.size !== 1) {
        setStatus('Combine blocked: selected vortices must all have the same boundary class.');
        return;
    }

    const degree = new Map();
    const pointByKey = new Map();
    for (const edge of selectedEdges) {
        for (const point of edge.points) {
            const key = canonicalPointKey(point);
            pointByKey.set(key, point);
            degree.set(key, (degree.get(key) || 0) + 1);
        }
    }
    const endpoints = [...degree.entries()].filter(([, value]) => value === 1).map(([key]) => key);
    const invalidDegree = [...degree.values()].some(value => value < 1 || value > 2);
    if (invalidDegree || endpoints.length !== 2) {
        setStatus('Combine blocked: selected vortices must form one simple open chain without branches or loops.');
        return;
    }

    const firstEdge = selectedEdges[0];
    const reachable = new Set([firstEdge.id]);
    const queue = [firstEdge.id];
    while (queue.length) {
        const current = queue.shift();
        for (const neighbor of layer.edgeAdjacency.get(current) || []) {
            if (selectedIds.has(neighbor) && !reachable.has(neighbor)) {
                reachable.add(neighbor);
                queue.push(neighbor);
            }
        }
    }
    if (reachable.size !== selectedIds.size) {
        setStatus('Combine blocked: selected vortices are not one connected chain.');
        return;
    }

    const internalKeys = [...degree.entries()].filter(([, value]) => value === 2).map(([key]) => key);
    for (const key of internalKeys) {
        const incident = new Set((layer.adjacency.get(key) || []).map(item => item.edgeId));
        if ([...incident].some(id => !selectedIds.has(id))) {
            setStatus('Combine blocked: an intermediate point is a junction used by another vortice.');
            return;
        }
    }

    if (!window.confirm(`Combine ${selectedIds.size} selected vortices into one straight vortice? Intermediate geometry points will be removed.`)) return;

    const removeKeys = new Set(internalKeys);
    const affectedCodes = new Set();
    const proposed = layer.areas.map(area => {
        let changed = false;
        const rings = area.rings.map(ring => normalizeRing(ring.filter(point => {
            const remove = removeKeys.has(canonicalPointKey(point));
            if (remove) changed = true;
            return !remove;
        })));
        if (changed) affectedCodes.add(area.code);
        return { ...area, rings };
    });
    const invalid = proposed.find(area => area.rings.some(ring => ring.length < 3));
    if (invalid) {
        setStatus(`Combine blocked: ${invalid.name} would contain an invalid polygon ring.`);
        return;
    }

    ensureSessionBaseForCodes(layer, affectedCodes);
    recordHistory('combine vortices');
    const previousEdges = layer.edges;
    const combinedClass = [...classSet][0];
    const endpointA = pointByKey.get(endpoints[0]);
    const endpointB = pointByKey.get(endpoints[1]);
    layer.areas = proposed;
    for (const code of affectedCodes) layer.changedAreaCodes.add(code);
    layer.geometryRevision += 1;
    buildLayerModel(layer, previousEdges);

    const combinedId = edgeIdFromPoints(endpointA, endpointB);
    const combinedEdge = layer.edges.get(combinedId);
    state.selectedVertices.clear();
    state.primaryVertexKey = null;
    if (combinedEdge) {
        combinedEdge.currentClass = combinedClass;
        state.selectedEdgeIds = new Set([combinedId]);
        state.primaryEdgeId = combinedId;
        state.selectedEdgeId = combinedId;
        setStatus(`Combined ${selectedIds.size} vortices into one.${topologyIssueSuffix(layer)}`);
    } else {
        state.selectedEdgeIds.clear();
        state.primaryEdgeId = null;
        state.selectedEdgeId = null;
        setStatus('Vortices combined, but the replacement segment could not be selected automatically.');
    }
    updateModifiedUi();
    updateSelectionUi();
    render();
}

function deleteSelectedVertices() {
    const layer = activeLayer();
    if (!state.advanced || !layer || !state.selectedVertices.size) return;
    if (!window.confirm(`Delete ${state.selectedVertices.size} selected vertices from every polygon that uses them?`)) return;
    const selected = new Set(state.selectedVertices);
    const affectedCodes = new Set();
    const proposed = layer.areas.map(area => {
        let changed = false;
        const rings = area.rings.map(ring => normalizeRing(ring.filter(point => {
            const remove = selected.has(canonicalPointKey(point));
            if (remove) changed = true;
            return !remove;
        })));
        if (changed) affectedCodes.add(area.code);
        return { ...area, rings };
    });
    const invalid = proposed.find(area => area.rings.some(ring => ring.length < 3));
    if (invalid) {
        setStatus(`Delete blocked: ${invalid.name} would contain an invalid polygon ring.`);
        return;
    }
    ensureSessionBaseForCodes(layer, affectedCodes);
    recordHistory('delete points');
    const previousEdges = layer.edges;
    layer.areas = proposed;
    for (const code of affectedCodes) layer.changedAreaCodes.add(code);
    layer.geometryRevision += 1;
    buildLayerModel(layer, previousEdges);
    state.selectedVertices.clear();
    state.primaryVertexKey = null;
    state.selectedEdgeId = null;
    state.selectedEdgeIds.clear();
    state.primaryEdgeId = null;
    updateModifiedUi();
    updateSelectionUi();
    setStatus(`Selected points deleted. New connecting vortices inherited the nearest previous boundary class.${topologyIssueSuffix(layer)}`);
    render();
}


function deleteSelectedEdges() {
    const layer = activeLayer();
    if (!layer || !state.selectedEdgeIds.size) return;
    const editable = [...state.selectedEdgeIds]
        .map(id => layer.edges.get(id))
        .filter(edge => edge && edge.currentClass !== 'none');
    if (!editable.length) {
        setStatus('Selected vortices are already deleted from boundary rendering.');
        return;
    }
    if (!window.confirm(`Delete ${editable.length} selected edge${editable.length === 1 ? '' : 's'} from the rendered boundary resources? Polygon fills remain intact.`)) return;
    recordHistory('delete edge');
    for (const edge of editable) edge.currentClass = 'none';
    updateModifiedUi();
    updateSelectionUi();
    setStatus(`Deleted ${editable.length} boundary edge${editable.length === 1 ? '' : 's'} from rendering. Undo or Revert restores them.`);
    render();
}

function quantizedPointOnEdge(layer, edge, clientX, clientY) {
    const rect = elements.canvas.getBoundingClientRect();
    const click = screenToWorld(clientX - rect.left, clientY - rect.top);
    const vx = edge.x2 - edge.x1;
    const vy = edge.y2 - edge.y1;
    const lengthSquared = vx * vx + vy * vy;
    if (lengthSquared <= 0) return null;
    const ratio = Math.max(0, Math.min(1, ((click.x - edge.x1) * vx + (click.y - edge.y1) * vy) / lengthSquared));
    const worldX = edge.x1 + ratio * vx;
    const worldY = edge.y1 + ratio * vy;
    const geo = inverseProject(worldX, worldY);
    return [
        Math.round(geo.longitude * layer.quantization),
        Math.round(geo.latitude * layer.quantization),
    ];
}

function insertPointOnEdge(edge, clientX, clientY) {
    const layer = activeLayer();
    if (!state.advanced || !state.addPointMode || !layer || !edge) return;
    const newPoint = quantizedPointOnEdge(layer, edge, clientX, clientY);
    if (!newPoint) return;
    const newKey = canonicalPointKey(newPoint);
    const endpointKeys = new Set(edge.points.map(canonicalPointKey));
    if (endpointKeys.has(newKey)) {
        setStatus('Add point blocked: at this map quantization the click resolves to an existing endpoint. Zoom in and click farther from the endpoint.');
        return;
    }
    if (layer.vertices.has(newKey)) {
        setStatus('Add point blocked: that exact geometry point already exists.');
        return;
    }

    const occurrencesByRing = new Map();
    const affectedCodes = new Set();
    for (const occurrence of edge.occurrences) {
        const key = `${occurrence.areaIndex}:${occurrence.ringIndex}`;
        if (!occurrencesByRing.has(key)) occurrencesByRing.set(key, []);
        occurrencesByRing.get(key).push(occurrence.edgeIndex);
        affectedCodes.add(layer.areas[occurrence.areaIndex].code);
    }
    if (!affectedCodes.size) {
        setStatus('Add point blocked: selected vortice has no polygon occurrence.');
        return;
    }

    const proposed = layer.areas.map((area, areaIndex) => ({
        ...area,
        rings: area.rings.map((ring, ringIndex) => {
            const indexes = occurrencesByRing.get(`${areaIndex}:${ringIndex}`);
            if (!indexes?.length) return ring;
            const copy = ring.map(point => [point[0], point[1]]);
            for (const edgeIndex of [...indexes].sort((a, b) => b - a)) copy.splice(edgeIndex + 1, 0, [newPoint[0], newPoint[1]]);
            return normalizeRing(copy);
        }),
    }));
    const invalid = proposed.find(area => area.rings.some(ring => ring.length < 3));
    if (invalid) {
        setStatus(`Add point blocked: ${invalid.name} would contain an invalid polygon ring.`);
        return;
    }

    ensureSessionBaseForCodes(layer, affectedCodes);
    recordHistory('add point');
    const previousEdges = layer.edges;
    const previousClass = {
        currentClass: edge.currentClass,
        baselineClass: edge.baselineClass,
        savedClass: edge.savedClass,
    };
    layer.areas = proposed;
    for (const code of affectedCodes) layer.changedAreaCodes.add(code);
    layer.geometryRevision += 1;
    buildLayerModel(layer, previousEdges);

    const splitIds = edge.points.map(point => edgeIdFromPoints(point, newPoint));
    for (const id of splitIds) {
        const split = layer.edges.get(id);
        if (!split) continue;
        split.currentClass = previousClass.currentClass;
        split.baselineClass = previousClass.baselineClass;
        split.savedClass = previousClass.savedClass;
    }
    state.selectedVertices = layer.vertices.has(newKey) ? new Set([newKey]) : new Set();
    state.primaryVertexKey = layer.vertices.has(newKey) ? newKey : null;
    state.selectedEdgeIds = new Set(splitIds.filter(id => layer.edges.has(id)));
    state.primaryEdgeId = [...state.selectedEdgeIds].at(-1) || null;
    state.selectedEdgeId = state.primaryEdgeId;
    state.addPointMode = false;
    updateModifiedUi();
    updateSelectionUi();
    setStatus(`Point added. The original vortice was split and both new edges kept its assignment.${topologyIssueSuffix(layer)}`);
    render();
}

function toggleAddPointMode() {
    if (!state.advanced) return;
    state.addPointMode = !state.addPointMode;
    if (state.addPointMode) {
        state.moveMode = false;
        state.movePreview = null;
    }
    updateAdvancedUi();
    render();
}

function toggleMoveMode() {
    if (!state.advanced || !state.selectedVertices.size) return;
    state.moveMode = !state.moveMode;
    state.addPointMode = false;
    state.movePreview = null;
    updateAdvancedUi();
    render();
}

function applyTopologyOverridePayload(layer, overridePayload) {
    layer.coastAllExterior = Boolean(overridePayload?.coastAllExterior);
    layer.explicitOverrideClassById = new Map();
    for (const item of overridePayload?.overrides || []) {
        if (!item?.id || !item?.to) continue;
        layer.explicitOverrideClassById.set(String(item.id), String(item.to));
    }
    layer.ownerOverrideById = new Map();
    for (const item of overridePayload?.ownerOverrides || []) {
        if (!item?.id || !Array.isArray(item.owners)) continue;
        layer.ownerOverrideById.set(String(item.id), item.owners.map(String));
    }
    layer.manualEdges = new Map();
    for (const item of overridePayload?.manualEdges || []) {
        if (!item?.id || !Array.isArray(item.points) || item.points.length !== 2) continue;
        layer.manualEdges.set(String(item.id), {
            id: String(item.id),
            points: item.points.map(point => [Number(point[0]), Number(point[1])]),
            owners: Array.isArray(item.owners) ? item.owners.map(String) : [],
            class: item.class || 'fine',
            baselineClass: item.baselineClass || 'none',
        });
    }
}

function currentOverridePayload(layer) {
    const changedCodes = layer.changedAreaCodes || new Set();
    const geometryAreas = layer.areas
        .filter(area => changedCodes.has(area.code))
        .map(area => ({
            code: area.code,
            name: area.name,
            rings: area.rings.map(ring => ring.map(point => [point[0], point[1]])),
        }));
    const geometryEdgeClasses = [...layer.edges.values()]
        .filter(edge => edge.ownerAreas.some(area => changedCodes.has(area.code)))
        .map(edge => ({
            id: edge.id,
            class: edge.currentClass,
            baselineClass: edge.baselineClass,
        }));
    const ownerOverrides = [...(layer.ownerOverrideById || new Map()).entries()].map(([id, owners]) => ({ id, owners: [...owners] }));
    const manualEdges = [...(layer.manualEdges || new Map()).values()].map(item => {
        const edge = layer.edges.get(item.id);
        return {
            id: item.id,
            points: item.points.map(point => [point[0], point[1]]),
            owners: edge ? edge.ownerAreas.map(area => area.code) : [...(item.owners || [])],
            class: edge?.currentClass || item.class || 'fine',
            baselineClass: item.baselineClass || 'none',
        };
    });
    return {
        version: 4,
        description: layer.key === 'municipality'
            ? 'Manual shared-boundary and geometry edits created by the QuakeDeck map editor.'
            : 'Manual JMA reporting-area edits created by the QuakeDeck map editor.',
        geometryEdited: geometryAreas.length > 0,
        geometryAreas,
        geometryEdgeClasses,
        ownerOverrides,
        manualEdges,
        coastAllExterior: Boolean(layer.coastAllExterior),
        overrides: [...layer.edges.values()]
            .filter(edge => {
                if (layer.coastAllExterior && edge.ownerIndexes.length === 1) return edge.currentClass !== 'coast';
                return edge.currentClass !== edge.baselineClass;
            })
            .map(edge => ({
                id: edge.id,
                from: layer.coastAllExterior && edge.ownerIndexes.length === 1 ? 'coast' : edge.baselineClass,
                to: edge.currentClass,
                owners: edge.ownerAreas.map(area => ({ code: area.code, name: area.name })),
            })),
    };
}

function applyGeometryAreaOverrides(areas, overridePayload) {
    const byCode = new Map((overridePayload.geometryAreas || []).map(item => [String(item.code), item]));
    if (!byCode.size) return new Set();
    const applied = new Set();
    for (const area of areas) {
        const override = byCode.get(area.code);
        if (!override || !Array.isArray(override.rings)) continue;
        const rings = override.rings
            .map(ring => normalizeRing(ring.map(point => [Number(point[0]), Number(point[1])])))
            .filter(ring => ring.length >= 3);
        if (rings.length) {
            area.rings = rings;
            applied.add(area.code);
        }
    }
    return applied;
}

async function ensureLayerBaseline(layer) {
    const firstBaselinePath = Object.values(layer.paths.baseline)[0];
    if (await fetchOptionalArrayBuffer(firstBaselinePath)) return;
    const writes = [];
    for (const [name, baselinePath] of Object.entries(layer.paths.baseline)) {
        const sourcePath = layer.paths[name];
        let buffer = layer.rawFiles.get(sourcePath);
        if (!buffer && name === 'overrides') buffer = utf8ToArrayBuffer(JSON.stringify({ version: 2, overrides: [] }, null, 2) + '\n');
        if (!buffer) continue;
        writes.push({ path: baselinePath, encoding: 'base64', content: bytesToBase64(buffer) });
    }
    if (writes.length) {
        await fetchJson('/api/write', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ writes }),
        });
    }
}

async function saveLayer(layer) {
    await ensureLayerBaseline(layer);
    const writes = [];
    if (layer.key === 'municipality') {
        const [geometry, fine, warning, prefecture] = await Promise.all([
            encodeMunicipalityGeometry(layer),
            encodeBoundaryResource(layer, 'fine'),
            encodeBoundaryResource(layer, 'warning'),
            encodeBoundaryResource(layer, 'prefecture'),
        ]);
        const overrideText = JSON.stringify(currentOverridePayload(layer), null, 2) + '\n';
        writes.push(
            { path: layer.paths.geometry, encoding: 'base64', content: bytesToBase64(geometry) },
            { path: layer.paths.fine, encoding: 'base64', content: bytesToBase64(fine) },
            { path: layer.paths.warning, encoding: 'base64', content: bytesToBase64(warning) },
            { path: layer.paths.prefecture, encoding: 'base64', content: bytesToBase64(prefecture) },
            { path: layer.paths.overrides, encoding: 'utf8', content: overrideText },
        );
        layer.rawFiles.set(layer.paths.geometry, geometry);
        layer.rawFiles.set(layer.paths.fine, fine);
        layer.rawFiles.set(layer.paths.warning, warning);
        layer.rawFiles.set(layer.paths.prefecture, prefecture);
        layer.rawFiles.set(layer.paths.overrides, utf8ToArrayBuffer(overrideText));
    } else {
        const [geometry, borders] = await Promise.all([
            gzipJson(encodeJmaAreaRoot(layer)),
            encodeReportingBorders(layer),
        ]);
        const overrideText = JSON.stringify(currentOverridePayload(layer), null, 2) + '\n';
        writes.push(
            { path: layer.paths.geometry, encoding: 'base64', content: bytesToBase64(geometry) },
            { path: layer.paths.borders, encoding: 'base64', content: bytesToBase64(borders) },
            { path: layer.paths.overrides, encoding: 'utf8', content: overrideText },
        );
        layer.rawFiles.set(layer.paths.geometry, geometry);
        layer.rawFiles.set(layer.paths.borders, borders);
        layer.rawFiles.set(layer.paths.overrides, utf8ToArrayBuffer(overrideText));
    }
    await fetchJson('/api/write', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ writes }),
    });
    layer.savedClassById = new Map();
    for (const edge of layer.edges.values()) {
        edge.savedClass = edge.currentClass;
        layer.savedClassById.set(edge.id, edge.currentClass);
    }
    layer.savedGeometryRevision = layer.geometryRevision;
    layer.savedTopologyRevision = layer.topologyRevision || 0;
}

async function saveAll() {
    setStatus('Saving map edits…');
    const dirtyLayers = [...state.layers.values()].filter(layer => {
        const counts = layerModifiedCounts(layer);
        return counts.unsavedClasses > 0 || counts.unsavedGeometry || counts.unsavedTopology;
    });
    if (!dirtyLayers.length) {
        setStatus('Nothing new to save.');
        return;
    }
    const issueCount = dirtyLayers.reduce((sum, layer) => sum + (layer.topologyIssues?.length || 0), 0);
    if (issueCount && !window.confirm(`There are ${issueCount} topology error${issueCount === 1 ? '' : 's'} in the edited layer(s). Save anyway?`)) {
        setStatus('Save cancelled because topology errors remain.');
        return;
    }
    for (const layer of dirtyLayers) await saveLayer(layer);
    updateModifiedUi();
    setStatus(`Saved ${dirtyLayers.length} edited layer${dirtyLayers.length === 1 ? '' : 's'}.`);
}

async function restoreActiveBaseline() {
    const layer = activeLayer();
    if (!state.advanced || !layer) return;
    if (!window.confirm(`Restore the saved baseline for ${layer.label}? This reverts every saved map-editor change in that layer.`)) return;
    const writes = [];
    for (const [name, baselinePath] of Object.entries(layer.paths.baseline)) {
        const buffer = await fetchOptionalArrayBuffer(baselinePath);
        if (!buffer) {
            setStatus('No saved baseline exists for this layer yet.');
            return;
        }
        const targetPath = layer.paths[name];
        if (!targetPath) continue;
        writes.push({ path: targetPath, encoding: 'base64', content: bytesToBase64(buffer) });
    }
    await fetchJson('/api/write', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ writes }),
    });
    setStatus('Baseline restored. Reloading map data…');
    await loadData();
}

function parseOverrides(text) {
    if (!text) return { version: 2, overrides: [] };
    try {
        const value = JSON.parse(text);
        return value && typeof value === 'object' ? value : { version: 2, overrides: [] };
    } catch {
        return { version: 2, overrides: [] };
    }
}

function applyTranslations(layer, kind) {
    const dictionary = kind === 'municipality'
        ? state.translations.municipality || {}
        : state.translations.epicenter || {};
    for (const area of layer.areas) area.nameEn = dictionary[area.name] || area.name;
}

function prepareLoadedClasses(layer, sets, overridePayload) {
    const loadedClassById = new Map();
    if (layer.key === 'municipality') {
        for (const id of sets.fine) loadedClassById.set(id, 'fine');
        for (const id of sets.warning) loadedClassById.set(id, 'warning');
        for (const id of sets.prefecture) loadedClassById.set(id, 'prefecture');
    } else {
        for (const id of sets.prefecture) loadedClassById.set(id, 'prefecture');
    }
    const baselineClassById = new Map();
    const savedClassById = new Map();
    for (const [id, currentClass] of loadedClassById.entries()) {
        baselineClassById.set(id, currentClass);
        savedClassById.set(id, currentClass);
    }
    for (const item of overridePayload.geometryEdgeClasses || []) {
        if (!item?.id || !item.class) continue;
        loadedClassById.set(item.id, item.class);
        savedClassById.set(item.id, item.class);
        baselineClassById.set(item.id, item.baselineClass || item.class);
    }
    for (const item of overridePayload.overrides || []) {
        if (!item?.id) continue;
        if (item.from) baselineClassById.set(item.id, item.from);
        if (item.to) {
            loadedClassById.set(item.id, item.to);
            savedClassById.set(item.id, item.to);
        }
    }
    layer.loadedClassById = loadedClassById;
    layer.baselineClassById = baselineClassById;
    layer.savedClassById = savedClassById;
}

async function loadData() {
    ensureCompressionSupport();
    elements.layerSelect.disabled = true;
    elements.reloadButton.disabled = true;
    elements.saveButton.disabled = true;
    setStatus('Loading map geometry…');

    state.meta = await fetchJson('/api/meta');
    elements.projectRoot.textContent = state.meta.projectRoot;

    const [placeNamesBuffer,
        muniGeometryGz, muniFineGz, muniWarningGz, muniPrefectureGz, muniOverridesText,
        jmaGeometryGz, jmaBordersGz, jmaOverridesText] = await Promise.all([
        fetchArrayBuffer(PATHS.placeNames),
        fetchArrayBuffer(PATHS.municipality.geometry),
        fetchArrayBuffer(PATHS.municipality.fine),
        fetchArrayBuffer(PATHS.municipality.warning),
        fetchArrayBuffer(PATHS.municipality.prefecture),
        fetchOptionalText(PATHS.municipality.overrides),
        fetchArrayBuffer(PATHS.jma.geometry),
        fetchArrayBuffer(PATHS.jma.borders),
        fetchOptionalText(PATHS.jma.overrides),
    ]);

    state.translations = JSON.parse(new TextDecoder().decode(placeNamesBuffer));

    const [muniGeometryBuffer, muniFineBuffer, muniWarningBuffer, muniPrefectureBuffer, jmaRoot] = await Promise.all([
        gunzip(muniGeometryGz),
        gunzip(muniFineGz),
        gunzip(muniWarningGz),
        gunzip(muniPrefectureGz),
        gunzipJson(jmaGeometryGz),
    ]);

    const municipalityParsed = parseMunicipalityGeometry(muniGeometryBuffer);
    const muniOverrides = parseOverrides(muniOverridesText);
    const municipalityChangedAreas = applyGeometryAreaOverrides(municipalityParsed.areas, muniOverrides);
    const municipality = {
        key: 'municipality',
        label: 'Municipalities — deep zoom',
        quantization: municipalityParsed.quantization,
        areas: municipalityParsed.areas,
        allowedClasses: ['fine', 'warning', 'prefecture', 'coast'],
        geometryRevision: 0,
        savedGeometryRevision: 0,
        topologyRevision: 0,
        savedTopologyRevision: 0,
        changedAreaCodes: municipalityChangedAreas,
        paths: PATHS.municipality,
        rawFiles: new Map([
            [PATHS.municipality.geometry, muniGeometryGz],
            [PATHS.municipality.fine, muniFineGz],
            [PATHS.municipality.warning, muniWarningGz],
            [PATHS.municipality.prefecture, muniPrefectureGz],
            [PATHS.municipality.overrides, muniOverridesText ? utf8ToArrayBuffer(muniOverridesText) : utf8ToArrayBuffer('{"version":3,"geometryAreas":[],"geometryEdgeClasses":[],"overrides":[]}\n')],
        ]),
    };
    applyTranslations(municipality, 'municipality');
    prepareLoadedClasses(municipality, {
        fine: parseBoundaryResource(muniFineBuffer).edges,
        warning: parseBoundaryResource(muniWarningBuffer).edges,
        prefecture: parseBoundaryResource(muniPrefectureBuffer).edges,
    }, muniOverrides);
    applyTopologyOverridePayload(municipality, muniOverrides);
    buildLayerModel(municipality);

    const jmaParsed = parseJmaAreaRoot(jmaRoot);
    const jmaOverrides = parseOverrides(jmaOverridesText);
    const jmaChangedAreas = applyGeometryAreaOverrides(jmaParsed.areas, jmaOverrides);
    const jmaBorders = parseReportingBorders(await gunzip(jmaBordersGz));
    const jma = {
        key: 'jma',
        label: 'JMA reporting areas — middle zoom',
        quantization: jmaParsed.quantization,
        areas: jmaParsed.areas,
        sourceRoot: jmaParsed.root,
        allowedClasses: ['fine', 'prefecture', 'coast'],
        geometryRevision: 0,
        savedGeometryRevision: 0,
        topologyRevision: 0,
        savedTopologyRevision: 0,
        changedAreaCodes: jmaChangedAreas,
        paths: PATHS.jma,
        rawFiles: new Map([
            [PATHS.jma.geometry, jmaGeometryGz],
            [PATHS.jma.borders, jmaBordersGz],
            [PATHS.jma.overrides, jmaOverridesText ? utf8ToArrayBuffer(jmaOverridesText) : utf8ToArrayBuffer('{"version":3,"geometryAreas":[],"geometryEdgeClasses":[],"overrides":[]}\n')],
        ]),
    };
    applyTranslations(jma, 'jma');
    prepareLoadedClasses(jma, { prefecture: jmaBorders.prefectureEdges }, jmaOverrides);
    applyTopologyOverridePayload(jma, jmaOverrides);
    buildLayerModel(jma);

    state.layers = new Map([['municipality', municipality], ['jma', jma]]);
    if (!state.layers.has(state.activeLayerKey)) state.activeLayerKey = 'municipality';
    elements.layerSelect.value = state.activeLayerKey;
    elements.layerSelect.disabled = false;
    elements.reloadButton.disabled = false;
    elements.saveButton.disabled = false;
    state.selectedEdgeId = null;
    state.selectedEdgeIds.clear();
    state.primaryEdgeId = null;
    state.selectedVertices.clear();
    state.primaryVertexKey = null;
    state.moveMode = false;
    state.addPointMode = false;
    state.areaOperation = null;
    state.selectedAreaCodes.clear();
    state.hoverAreaCode = null;
    state.movePreview = null;
    state.undoStack.length = 0;
    state.redoStack.length = 0;
    for (const layer of state.layers.values()) layer.sessionBaseRings = new Map();
    updateHistoryUi();
    resizeCanvas();
    fitBounds(activeLayer().bounds);
    updateLayerUi();
    setStatus(`Loaded ${municipality.areas.length} municipalities and ${jma.areas.length} JMA reporting areas.`);
}

async function closeEditor() {
    elements.closeButton.disabled = true;
    setStatus('Stopping local editor server…');
    try {
        await fetchJson('/api/shutdown');
    } catch { /* server may disappear immediately */ }
    document.body.innerHTML = `
        <div style="font-family:Segoe UI,sans-serif;background:#0f1115;color:#e8edf5;min-height:100vh;display:grid;place-items:center;padding:30px">
            <div style="max-width:620px;text-align:center">
                <h1>QuakeDeck Map Editor closed</h1>
                <p style="color:#9aa6ba">The local Python server has stopped and released 127.0.0.1. You can close this browser tab.</p>
            </div>
        </div>`;
    setTimeout(() => window.close(), 150);
}

function bindUi() {
    elements.projectRoot = document.getElementById('project-root');
    elements.statusText = document.getElementById('status-text');
    elements.zoomText = document.getElementById('zoom-text');
    elements.modifiedCount = document.getElementById('modified-count');
    elements.layerSelect = document.getElementById('layer-select');
    elements.saveButton = document.getElementById('save-button');
    elements.reloadButton = document.getElementById('reload-button');
    elements.undoButton = document.getElementById('undo-button');
    elements.redoButton = document.getElementById('redo-button');
    elements.closeButton = document.getElementById('close-button');
    elements.searchInput = document.getElementById('search-input');
    elements.searchResults = document.getElementById('search-results');
    elements.selectionEmpty = document.getElementById('selection-empty');
    elements.selectionDetails = document.getElementById('selection-details');
    elements.selectedOwners = document.getElementById('selected-owners');
    elements.selectedRawOwners = document.getElementById('selected-raw-owners');
    elements.selectedCurrent = document.getElementById('selected-current');
    elements.selectedOriginal = document.getElementById('selected-original');
    elements.selectedId = document.getElementById('selected-id');
    elements.selectedVorticeCount = document.getElementById('selected-vortice-count');
    elements.selectedPrimaryVortice = document.getElementById('selected-primary-vortice');
    elements.selectedPointCount = document.getElementById('selected-point-count');
    elements.selectedPrimaryPoint = document.getElementById('selected-primary-point');
    elements.revertButton = document.getElementById('revert-button');
    elements.focusButton = document.getElementById('focus-button');
    elements.advancedToggle = document.getElementById('advanced-toggle');
    elements.moveButton = document.getElementById('move-button');
    elements.addPointButton = document.getElementById('add-point-button');
    elements.createEdgeButton = document.getElementById('create-edge-button');
    elements.combinePointsButton = document.getElementById('combine-points-button');
    elements.combineButton = document.getElementById('combine-button');
    elements.deleteButton = document.getElementById('delete-button');
    elements.deleteEdgeButton = document.getElementById('delete-edge-button');
    elements.reassignAreasButton = document.getElementById('reassign-areas-button');
    elements.massCoastButton = document.getElementById('mass-coast-button');
    elements.massWarningButton = document.getElementById('mass-warning-button');
    elements.massPrefectureButton = document.getElementById('mass-prefecture-button');
    elements.issuesButton = document.getElementById('issues-button');
    elements.topologySummary = document.getElementById('topology-summary');
    elements.topologyIssueList = document.getElementById('topology-issue-list');
    elements.areaOperationRow = document.getElementById('area-operation-row');
    elements.areaOperationLabel = document.getElementById('area-operation-label');
    elements.areaOperationHelp = document.getElementById('area-operation-help');
    elements.selectedAreaCount = document.getElementById('selected-area-count');
    elements.areaOperationClear = document.getElementById('area-operation-clear');
    elements.areaOperationConfirm = document.getElementById('area-operation-confirm');
    elements.areaOperationCancel = document.getElementById('area-operation-cancel');
    elements.restoreBaselineButton = document.getElementById('restore-baseline-button');
    elements.advancedStatus = document.getElementById('advanced-status');
    elements.toggleWarning = document.getElementById('toggle-warning');
    elements.canvas = document.getElementById('map-canvas');
    elements.basemapAttribution = document.getElementById('basemap-attribution');
    elements.mapArea = document.querySelector('.map-area');
    elements.modeChip = document.getElementById('mode-chip');

    elements.layerSelect.addEventListener('change', event => {
        state.activeLayerKey = event.target.value;
        state.moveMode = false;
        state.addPointMode = false;
        state.areaOperation = null;
        state.selectedAreaCodes.clear();
        state.hoverAreaCode = null;
        state.movePreview = null;
        updateLayerUi();
    });
    elements.saveButton.addEventListener('click', () => saveAll().catch(error => setStatus(error.message)));
    elements.reloadButton.addEventListener('click', () => loadData().catch(error => setStatus(error.message)));
    elements.undoButton.addEventListener('click', undoEdit);
    elements.redoButton.addEventListener('click', redoEdit);
    elements.closeButton.addEventListener('click', closeEditor);
    elements.searchInput.addEventListener('input', performSearch);
    elements.revertButton.addEventListener('click', revertSelectedBoundary);
    elements.focusButton.addEventListener('click', focusSelectedEdge);
    elements.reassignAreasButton.addEventListener('click', () => startAreaOperation('reassign'));
    elements.massCoastButton.addEventListener('click', () => startAreaOperation('mass-coast'));
    elements.massWarningButton.addEventListener('click', () => startAreaOperation('mass-warning'));
    elements.massPrefectureButton.addEventListener('click', () => startAreaOperation('mass-prefecture'));
    elements.issuesButton.addEventListener('click', focusNextTopologyIssue);
    elements.areaOperationClear.addEventListener('click', () => { state.selectedAreaCodes.clear(); updateAreaOperationUi(); render(); });
    elements.areaOperationConfirm.addEventListener('click', confirmAreaOperation);
    elements.areaOperationCancel.addEventListener('click', cancelAreaOperation);
    document.querySelectorAll('.class-button').forEach(button => button.addEventListener('click', () => applyClassToSelected(button.dataset.class)));

    elements.advancedToggle.addEventListener('change', event => {
        state.advanced = event.target.checked;
        if (!state.advanced) {
            state.moveMode = false;
            state.addPointMode = false;
            if (state.areaOperation === 'create-edge') cancelAreaOperation();
            state.movePreview = null;
            state.selectedVertices.clear();
            state.primaryVertexKey = null;
        }
        updateAdvancedUi();
        render();
    });
    elements.moveButton.addEventListener('click', toggleMoveMode);
    elements.addPointButton.addEventListener('click', toggleAddPointMode);
    elements.createEdgeButton.addEventListener('click', () => startAreaOperation('create-edge'));
    elements.combinePointsButton.addEventListener('click', combineSelectedPoints);
    elements.combineButton.addEventListener('click', combineSelectedVortices);
    elements.deleteButton.addEventListener('click', deleteSelectedVertices);
    elements.deleteEdgeButton.addEventListener('click', deleteSelectedEdges);
    elements.restoreBaselineButton.addEventListener('click', () => restoreActiveBaseline().catch(error => setStatus(error.message)));

    const toggles = {
        'toggle-basemap': 'basemap',
        'toggle-coast': 'coast',
        'toggle-fine': 'fine',
        'toggle-warning': 'warning',
        'toggle-prefecture': 'prefecture',
        'toggle-vertices': 'vertices',
        'toggle-modified': 'modified',
        'toggle-errors': 'errors',
        'toggle-labels': 'labels',
    };
    for (const [id, key] of Object.entries(toggles)) {
        document.getElementById(id).addEventListener('change', event => {
            state.show[key] = event.target.checked;
            if (key === 'basemap' && elements.basemapAttribution) {
                elements.basemapAttribution.style.display = state.show.basemap ? '' : 'none';
            }
            render();
        });
    }

    elements.canvas.addEventListener('pointerdown', event => {
        const rect = elements.canvas.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const y = event.clientY - rect.top;
        if (state.areaOperation) {
            state.pointer = {
                type: 'area-pan', pointerId: event.pointerId,
                startClientX: event.clientX, startClientY: event.clientY,
                lastClientX: event.clientX, lastClientY: event.clientY,
                moved: false,
            };
            elements.canvas.setPointerCapture(event.pointerId);
            return;
        }
        const hitVertex = state.advanced && state.moveMode ? findVertexAtScreen(event.clientX, event.clientY) : null;
        if (state.addPointMode) {
            state.pointer = {
                type: 'add-point', pointerId: event.pointerId,
                startClientX: event.clientX, startClientY: event.clientY, moved: false,
            };
        } else if (state.moveMode && hitVertex && state.selectedVertices.has(hitVertex.key)) {
            state.pointer = { type: 'move', pointerId: event.pointerId };
            state.movePreview = { startWorld: screenToWorld(x, y), mapping: new Map() };
        } else if (event.shiftKey) {
            state.pointer = {
                type: event.altKey && state.advanced ? 'point-box' : 'edge-box', pointerId: event.pointerId,
                startClientX: event.clientX, startClientY: event.clientY,
                moved: false,
            };
            state.selectionBox = { startX: x, startY: y, currentX: x, currentY: y };
        } else if (event.altKey) {
            // Alt is reserved for point selection. Never start a pan with Alt held;
            // this also prevents Windows/browser menu activation from nudging the camera.
            state.pointer = {
                type: state.advanced ? 'point-click' : 'alt-idle', pointerId: event.pointerId,
                startClientX: event.clientX, startClientY: event.clientY,
                moved: false,
            };
        } else {
            state.pointer = {
                type: 'pan', pointerId: event.pointerId,
                startClientX: event.clientX, startClientY: event.clientY,
                lastClientX: event.clientX, lastClientY: event.clientY,
                moved: false,
                clickEvent: { ctrlKey: event.ctrlKey, shiftKey: event.shiftKey, altKey: false },
            };
        }
        elements.canvas.setPointerCapture(event.pointerId);
    });

    elements.canvas.addEventListener('pointermove', event => {
        if (!state.pointer) {
            if (state.areaOperation) {
                const area = findSelectableAreaAtScreen(event.clientX, event.clientY);
                const nextCode = area?.code || null;
                if (nextCode !== state.hoverAreaCode) {
                    state.hoverAreaCode = nextCode;
                    render();
                }
            }
            return;
        }
        if (state.pointer.pointerId !== event.pointerId) return;
        if (state.pointer.type === 'move') {
            previewMove(event.clientX, event.clientY);
            return;
        }
        if (state.pointer.type === 'edge-box' || state.pointer.type === 'point-box') {
            const rect = elements.canvas.getBoundingClientRect();
            state.selectionBox.currentX = event.clientX - rect.left;
            state.selectionBox.currentY = event.clientY - rect.top;
            if (Math.abs(event.clientX - state.pointer.startClientX) > 3 || Math.abs(event.clientY - state.pointer.startClientY) > 3) state.pointer.moved = true;
            render();
            return;
        }
        if (state.pointer.type === 'add-point' || state.pointer.type === 'point-click' || state.pointer.type === 'alt-idle') {
            if (Math.abs(event.clientX - state.pointer.startClientX) > 3 || Math.abs(event.clientY - state.pointer.startClientY) > 3) state.pointer.moved = true;
            return;
        }
        const dx = event.clientX - state.pointer.lastClientX;
        const dy = event.clientY - state.pointer.lastClientY;
        if (Math.abs(event.clientX - state.pointer.startClientX) > 3 || Math.abs(event.clientY - state.pointer.startClientY) > 3) state.pointer.moved = true;
        state.viewport.offsetX += dx;
        state.viewport.offsetY += dy;
        state.pointer.lastClientX = event.clientX;
        state.pointer.lastClientY = event.clientY;
        render();
    });

    elements.canvas.addEventListener('pointerup', event => {
        if (!state.pointer || state.pointer.pointerId !== event.pointerId) return;
        const pointer = state.pointer;
        state.pointer = null;
        try { elements.canvas.releasePointerCapture(event.pointerId); } catch { /* ignore */ }
        if (pointer.type === 'area-pan') {
            if (!pointer.moved) {
                const area = findSelectableAreaAtScreen(event.clientX, event.clientY);
                if (area) toggleAreaSelection(area);
            }
            return;
        }
        if (pointer.type === 'move') {
            const mapping = state.movePreview?.mapping || new Map();
            state.movePreview = null;
            if (mapping.size && applyPointMapping(mapping, null, 'move points')) setStatus(`Moved ${mapping.size} selected points.${topologyIssueSuffix(activeLayer())}`);
            return;
        }
        if (pointer.type === 'add-point') {
            if (!pointer.moved) {
                const edge = findEdgeAtScreen(event.clientX, event.clientY);
                if (edge) insertPointOnEdge(edge, event.clientX, event.clientY);
                else setStatus('Add point: click directly on a vortice.');
            }
            return;
        }
        if (pointer.type === 'point-click' || pointer.type === 'alt-idle') {
            if (pointer.type === 'point-click' && !pointer.moved) {
                const vertex = findVertexAtScreen(event.clientX, event.clientY);
                if (vertex) selectVertex(vertex, { shiftKey: false });
                else {
                    state.selectedVertices.clear();
                    state.primaryVertexKey = null;
                    updateSelectionUi();
                    render();
                }
            }
            return;
        }
        if (pointer.type === 'edge-box' || pointer.type === 'point-box') {
            const box = state.selectionBox;
            state.selectionBox = null;
            if (pointer.moved && box) {
                if (pointer.type === 'point-box') selectVerticesInBox(box);
                else selectEdgesInBox(box);
            } else if (pointer.type === 'point-box') {
                const vertex = findVertexAtScreen(event.clientX, event.clientY);
                if (vertex) selectVertex(vertex, { shiftKey: true });
            } else {
                const edge = findEdgeAtScreen(event.clientX, event.clientY);
                if (edge) selectEdge(edge, { shiftKey: true });
                else clearSelection();
            }
            return;
        }
        if (!pointer.moved) {
            const edge = findEdgeAtScreen(event.clientX, event.clientY);
            if (edge) selectEdge(edge, pointer.clickEvent);
            else clearSelection();
        }
    });

    elements.canvas.addEventListener('wheel', event => {
        event.preventDefault();
        const rect = elements.canvas.getBoundingClientRect();
        const anchorX = event.clientX - rect.left;
        const anchorY = event.clientY - rect.top;
        const before = screenToWorld(anchorX, anchorY);
        const factor = event.deltaY < 0 ? 1.28 : 1 / 1.28;
        const minScale = state.viewport.fitScale * MIN_ZOOM_MULTIPLIER;
        const maxScale = state.viewport.fitScale * MAX_ZOOM_MULTIPLIER;
        state.viewport.scale = Math.max(minScale, Math.min(maxScale, state.viewport.scale * factor));
        state.viewport.offsetX = anchorX - before.x * state.viewport.scale;
        state.viewport.offsetY = anchorY - before.y * state.viewport.scale;
        updateZoomText();
        render();
    }, { passive: false });

    elements.canvas.addEventListener('dblclick', () => {
        fitBounds(activeLayer()?.bounds);
        render();
    });

    window.addEventListener('resize', () => {
        resizeCanvas();
        fitBounds(activeLayer()?.bounds);
        render();
    });

    window.addEventListener('keydown', event => {
        if (event.key === 'Alt') {
            event.preventDefault();
            event.stopPropagation();
            return;
        }
        const tag = event.target?.tagName;
        const typing = tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT';
        if (!typing && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
            event.preventDefault();
            if (event.shiftKey) redoEdit();
            else undoEdit();
            return;
        }
        if (!typing && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'y') {
            event.preventDefault();
            redoEdit();
            return;
        }
        if (event.key === 'Escape' && (state.moveMode || state.addPointMode || state.areaOperation)) {
            state.moveMode = false;
            state.addPointMode = false;
            state.movePreview = null;
            if (state.areaOperation) cancelAreaOperation();
            updateAdvancedUi();
            render();
        }
    }, true);
    window.addEventListener('keyup', event => {
        if (event.key === 'Alt') {
            event.preventDefault();
            event.stopPropagation();
        }
    }, true);
}

let lifecycleTimer = null;

async function pingEditorServer() {
    try {
        await fetch('/api/ping', { cache: 'no-store' });
    } catch { /* server may already be closing */ }
}

function startEditorLifecycle() {
    pingEditorServer();
    lifecycleTimer = window.setInterval(pingEditorServer, 1500);
    window.addEventListener('pagehide', () => {
        if (lifecycleTimer !== null) window.clearInterval(lifecycleTimer);
        try { navigator.sendBeacon('/api/disconnect', ''); } catch { /* best effort */ }
    }, { once: true });
}

async function main() {
    bindUi();
    startEditorLifecycle();
    resizeCanvas();
    try {
        await loadData();
        render();
    } catch (error) {
        console.error(error);
        setStatus(error.message);
    }
}

main();
