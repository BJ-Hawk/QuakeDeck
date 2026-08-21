import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { readFile } from "node:fs/promises";
import vm from "node:vm";


class MockClassList {
  constructor(initial = []) {
    this.values = new Set(initial);
  }

  add(...names) {
    names.forEach((name) => this.values.add(name));
  }

  remove(...names) {
    names.forEach((name) => this.values.delete(name));
  }

  contains(name) {
    return this.values.has(name);
  }
}


class MockElement {
  constructor(initialClasses = []) {
    this.classList = new MockClassList(initialClasses);
    this.children = [];
    this.listeners = new Map();
    this.className = "";
    this.textContent = "";
    this.disabled = false;
    this._innerHTML = "";
  }

  set innerHTML(value) {
    this._innerHTML = value;
    this.children = [];
  }

  get innerHTML() {
    return this._innerHTML;
  }

  addEventListener(name, listener) {
    this.listeners.set(name, listener);
  }

  append(...children) {
    this.children.push(...children);
  }

  appendChild(child) {
    this.children.push(child);
    return child;
  }
}


class MockStorage {
  constructor(entries) {
    this.values = new Map(Object.entries(entries));
  }

  getItem(key) {
    return this.values.get(key) ?? null;
  }

  setItem(key, value) {
    this.values.set(key, String(value));
  }

  removeItem(key) {
    this.values.delete(key);
  }
}


function jsonResponse(body) {
  return {
    ok: true,
    status: 200,
    statusText: "OK",
    json: async () => body,
  };
}


const elementIds = [
  "connect-button",
  "clear-button",
  "status-text",
  "status-dot",
  "message",
  "results",
  "result-summary",
  "available-list",
  "unavailable-list",
  "jquake-note",
  "download-report",
];
const elements = Object.fromEntries(elementIds.map((id) => [id, new MockElement()]));
elements.message.classList.add("hidden");
elements.results.classList.add("hidden");
elements["jquake-note"].classList.add("hidden");

const redirectUri = "https://bj-hawk.github.io/QuakeDeck/diagnostics.html";
const sessionStorage = new MockStorage({
  qd_oauth_verifier: "verifier",
  qd_oauth_state: "expected-state",
  qd_oauth_client_id: "CId.public-test",
  qd_oauth_redirect_uri: redirectUri,
});
const requests = [];

const context = {
  Blob,
  Date,
  Error,
  JSON,
  Math,
  Object,
  Set,
  String,
  TextEncoder,
  URL,
  URLSearchParams,
  Uint8Array,
  btoa,
  console,
  crypto: webcrypto,
  document: {
    title: "Your DM-D.S.S data — QuakeDeck",
    getElementById: (id) => elements[id] ?? null,
    createElement: () => new MockElement(),
  },
  fetch: async (url, options = {}) => {
    requests.push({ url: String(url), options });
    if (String(url).endsWith("/token")) {
      return jsonResponse({
        access_token: "ATn.test",
        refresh_token: "ARh.test",
        scope: "contract.list",
      });
    }
    if (String(url).endsWith("/contract")) {
      return jsonResponse({
        status: "ok",
        items: [
          {
            planName: "Emergency earthquake forecast",
            classification: "eew.forecast",
            isValid: true,
            connectionCounts: 1,
            start: "2026-08-21T12:49:38.500Z",
          },
          {
            planName: "Earthquake and tsunami",
            classification: "telegram.earthquake",
            isValid: false,
            connectionCounts: 0,
            start: null,
          },
          {
            planName: "JQuake exclusive",
            classification: "application.jquake",
            isValid: true,
            connectionCounts: 1,
            start: "2026-01-01T00:00:00.000Z",
          },
        ],
      });
    }
    if (String(url).endsWith("/revoke")) {
      return { ok: true, status: 200, statusText: "OK" };
    }
    throw new Error(`Unexpected request: ${url}`);
  },
  history: { replaceState() {} },
  sessionStorage,
  setTimeout,
  window: {
    QUAKEDECK_CONFIG: { clientId: "CId.public-test" },
    location: {
      origin: "https://bj-hawk.github.io",
      pathname: "/QuakeDeck/diagnostics.html",
      search: "?code=ACe.test&state=expected-state",
      assign() {},
    },
  },
};

vm.createContext(context);
const source = await readFile(new URL("../web/assets/diagnostics.js", import.meta.url), "utf8");
vm.runInContext(source, context, { filename: "diagnostics.js" });

for (let attempt = 0; attempt < 10 && elements.results.classList.contains("hidden"); attempt += 1) {
  await new Promise((resolve) => setTimeout(resolve, 0));
}

assert.equal(requests.length, 4, "token, contract, and two revoke requests expected");
assert.equal(requests.filter(({ url }) => url.includes("/socket")).length, 0, "socket APIs must not be called");
assert.equal(elements["available-list"].children.length, 1, "forecast plan should expose one capability");
assert.equal(elements["unavailable-list"].children.length, 7, "inactive plans should remain explanatory only");
assert.match(elements["result-summary"].textContent, /1 DM-D\.S\.S data capability/);
assert.equal(elements["jquake-note"].classList.contains("hidden"), false, "JQuake-only limitation should be shown");
assert.equal(elements.results.classList.contains("hidden"), false, "results should be visible");
assert.equal(elements["status-text"].textContent, "Access checked — disconnected");
assert.equal(sessionStorage.values.size, 0, "OAuth session values should be cleared");

console.log("Validated the DM-D.S.S OAuth callback and capability mapping without socket access.");
