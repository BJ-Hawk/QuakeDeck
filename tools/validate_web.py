"""Validate QuakeDeck's dependency-free static website."""

from __future__ import annotations

from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"


class PageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.ids: list[str] = []
        self.references: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if values.get("id"):
            self.ids.append(values["id"] or "")
        for attribute in ("href", "src"):
            if values.get(attribute):
                self.references.append(values[attribute] or "")


def validate_html() -> list[str]:
    errors: list[str] = []
    pages = sorted(WEB.glob("*.html"))
    if not pages:
        return ["No HTML pages found under web/. "]

    for page in pages:
        text = page.read_text(encoding="utf-8")
        parser = PageParser()
        parser.feed(text)

        duplicates = [value for value, count in Counter(parser.ids).items() if count > 1]
        if duplicates:
            errors.append(f"{page.name}: duplicate IDs: {', '.join(duplicates)}")

        for reference in parser.references:
            parts = urlsplit(reference)
            if parts.scheme or parts.netloc or reference.startswith(("#", "mailto:")):
                continue
            target = page.parent / parts.path
            if parts.path and not target.exists():
                errors.append(f"{page.name}: missing local reference {parts.path}")

        if "diagnostics.html" in text and page.name != "404.html" and "DM-D.S.S access" not in text:
            errors.append(f"{page.name}: DM-D.S.S navigation is not user-facing")

    return errors


def validate_diagnostics() -> list[str]:
    errors: list[str] = []
    html = (WEB / "diagnostics.html").read_text(encoding="utf-8")
    script = (WEB / "assets" / "diagnostics.js").read_text(encoding="utf-8")
    readme = (WEB / "README.md").read_text(encoding="utf-8")
    config = (WEB / "site-config.js").read_text(encoding="utf-8")

    required_html = {
        'id="connect-button"': "connect action",
        'id="available-list"': "available-capability list",
        'id="unavailable-list"': "unavailable-capability list",
        "Read-only plan check": "plain-language privacy promise",
        "QuakeDeck’s P2PQuake foundation": "P2PQuake baseline",
    }
    for marker, label in required_html.items():
        if marker not in html:
            errors.append(f"diagnostics.html: missing {label}")

    forbidden_html = ("client-id", "redirect-uri", "summary-json", "socket-test-list")
    for marker in forbidden_html:
        if marker in html:
            errors.append(f"diagnostics.html: developer-facing control remains: {marker}")

    if 'const SCOPES = ["contract.list"];' not in script:
        errors.append("diagnostics.js: OAuth must request only contract.list")

    if 'clientId: "CId.' not in config or 'clientId: ""' in config:
        errors.append("site-config.js: registered public OAuth client ID is not configured")

    forbidden_script = (
        "SOCKET_START_URL",
        "SOCKET_LIST_URL",
        "socket.start",
        "socket.close",
        "localStorage",
    )
    for marker in forbidden_script:
        if marker in script:
            errors.append(f"diagnostics.js: forbidden socket or persistent-storage behavior: {marker}")

    required_capabilities = (
        'classification: "eew.forecast"',
        'classification: "eew.warning"',
        'classification: "eew.realtime"',
        'classification: "telegram.earthquake"',
        "Observed tsunami data",
        "Long-period ground motion",
        "Special earthquake advisories",
    )
    for marker in required_capabilities:
        if marker not in script:
            errors.append(f"diagnostics.js: missing capability mapping: {marker}")

    stale_docs = ("socket.list", "socket.start", "socket.close", "Socket Start")
    for marker in stale_docs:
        if marker in readme:
            errors.append(f"web/README.md: stale probe behavior remains: {marker}")

    return errors


def main() -> None:
    errors = validate_html() + validate_diagnostics()
    css = (WEB / "assets" / "styles.css").read_text(encoding="utf-8")
    if css.count("{") != css.count("}"):
        errors.append("styles.css: unbalanced braces")

    if errors:
        raise SystemExit("\n".join(f"ERROR: {error}" for error in errors))

    page_count = len(list(WEB.glob("*.html")))
    print(f"Validated {page_count} HTML pages and the DM-D.S.S access flow.")


if __name__ == "__main__":
    main()
