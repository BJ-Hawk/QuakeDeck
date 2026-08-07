#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import json
import mimetypes
import os
import sys
import threading
import time
import webbrowser
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

TOOL_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = TOOL_ROOT.parent.parent
HOST = "127.0.0.1"
DEFAULT_PORT = 8765

STATIC_TYPES = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".svg": "image/svg+xml",
}


class BrowserLifecycle:
    def __init__(self):
        self.lock = threading.Lock()
        self.generation = 0

    def ping(self) -> int:
        with self.lock:
            self.generation += 1
            return self.generation

    def schedule_disconnect(self, server, delay: float = 3.0) -> None:
        with self.lock:
            generation = self.generation

        def worker():
            time.sleep(delay)
            with self.lock:
                if self.generation != generation:
                    return
            server.shutdown()

        threading.Thread(target=worker, daemon=True).start()


LIFECYCLE = BrowserLifecycle()


def json_bytes(payload: object) -> bytes:
    return json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")


def safe_project_path(relative_path: str) -> Path:
    relative = Path(relative_path.replace("\\", "/"))
    if relative.is_absolute():
        raise ValueError("Absolute paths are not allowed")
    resolved = (PROJECT_ROOT / relative).resolve()
    project_root_resolved = PROJECT_ROOT.resolve()
    if os.path.commonpath([str(project_root_resolved), str(resolved)]) != str(project_root_resolved):
        raise ValueError("Path escapes project root")
    return resolved


class Handler(BaseHTTPRequestHandler):
    server_version = "QuakeDeckMapEditor/1.0"

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/api/meta":
            self.respond_json(
                {
                    "projectRoot": str(PROJECT_ROOT),
                    "toolRoot": str(TOOL_ROOT),
                    "overridePath": "tools/source/jma_municipality_boundary_overrides.json",
                    "files": {
                        "municipalities": "app/src/main/res/raw/jma_quake_municipalities_topology.gz",
                        "fine": "app/src/main/res/raw/jma_municipality_fine_boundaries.gz",
                        "warning": "app/src/main/res/raw/jma_municipality_warning_boundaries.gz",
                        "prefecture": "app/src/main/res/raw/jma_municipality_prefecture_boundaries.gz",
                        "jmaQuakeAreas": "app/src/main/res/raw/jma_quake_regions.gz",
                        "jmaQuakeBorders": "app/src/main/res/raw/jma_quake_region_borders.gz",
                        "coastlines": "app/src/main/res/raw/japan_prefecture_coastlines_hires.gz",
                        "placeNames": "app/src/main/res/raw/jma_place_names.json"
                    },
                }
            )
            return
        if parsed.path == "/api/ping":
            LIFECYCLE.ping()
            self.respond_json({"ok": True})
            return
        if parsed.path == "/api/read":
            query = parse_qs(parsed.query)
            relative_path = query.get("path", [None])[0]
            if not relative_path:
                self.respond_error(HTTPStatus.BAD_REQUEST, "Missing path")
                return
            try:
                target = safe_project_path(relative_path)
            except ValueError as exc:
                self.respond_error(HTTPStatus.BAD_REQUEST, str(exc))
                return
            if not target.is_file():
                self.respond_error(HTTPStatus.NOT_FOUND, f"File not found: {relative_path}")
                return
            content_type = STATIC_TYPES.get(target.suffix.lower()) or mimetypes.guess_type(target.name)[0] or "application/octet-stream"
            data = target.read_bytes()
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if parsed.path == "/api/shutdown":
            self.respond_json({"ok": True})
            threading.Thread(target=self.server.shutdown, daemon=True).start()
            return
        self.serve_static(parsed.path)

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path == "/api/disconnect":
            self.send_response(HTTPStatus.NO_CONTENT)
            self.end_headers()
            LIFECYCLE.schedule_disconnect(self.server)
            return
        if parsed.path == "/api/shutdown":
            self.respond_json({"ok": True})
            threading.Thread(target=self.server.shutdown, daemon=True).start()
            return
        if parsed.path != "/api/write":
            self.respond_error(HTTPStatus.NOT_FOUND, "Unknown endpoint")
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.respond_error(HTTPStatus.BAD_REQUEST, "Invalid content length")
            return
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception as exc:  # noqa: BLE001
            self.respond_error(HTTPStatus.BAD_REQUEST, f"Invalid JSON: {exc}")
            return

        writes = payload.get("writes")
        if not isinstance(writes, list) or not writes:
            self.respond_error(HTTPStatus.BAD_REQUEST, "Payload must contain a non-empty writes list")
            return

        written = []
        try:
            for item in writes:
                relative_path = item["path"]
                encoding = item.get("encoding", "utf8")
                content = item["content"]
                target = safe_project_path(relative_path)
                target.parent.mkdir(parents=True, exist_ok=True)
                if encoding == "utf8":
                    target.write_text(content, encoding="utf-8")
                elif encoding == "base64":
                    target.write_bytes(base64.b64decode(content))
                else:
                    raise ValueError(f"Unsupported encoding: {encoding}")
                written.append(relative_path)
        except Exception as exc:  # noqa: BLE001
            self.respond_error(HTTPStatus.BAD_REQUEST, f"Write failed: {exc}")
            return

        self.respond_json({"ok": True, "written": written})

    def serve_static(self, raw_path: str) -> None:
        route = raw_path or "/"
        if route == "/":
            route = "/index.html"
        relative = route.lstrip("/")
        target = (TOOL_ROOT / relative).resolve()
        if not target.is_file() or os.path.commonpath([str(TOOL_ROOT), str(target)]) != str(TOOL_ROOT):
            self.respond_error(HTTPStatus.NOT_FOUND, "Static file not found")
            return
        data = target.read_bytes()
        content_type = STATIC_TYPES.get(target.suffix.lower()) or mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def respond_json(self, payload: object, status: int = HTTPStatus.OK) -> None:
        data = json_bytes(payload)
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def respond_error(self, status: int, message: str) -> None:
        self.respond_json({"ok": False, "error": message}, status=status)

    def log_message(self, format: str, *args):
        sys.stdout.write("%s - - [%s] %s\n" % (self.address_string(), self.log_date_time_string(), format % args))


def main() -> int:
    parser = argparse.ArgumentParser(description="Serve the QuakeDeck map editor")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()

    server = ThreadingHTTPServer((HOST, args.port), Handler)
    server.daemon_threads = True
    url = f"http://{HOST}:{args.port}/"
    print(f"QuakeDeck root: {PROJECT_ROOT}")
    print(f"Map editor: {url}")
    if not args.no_browser:
        webbrowser.open(url)
    try:
        server.serve_forever(poll_interval=0.1)
    except KeyboardInterrupt:
        print("\nShutting down…")
    finally:
        server.server_close()
    print("Map editor stopped.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
