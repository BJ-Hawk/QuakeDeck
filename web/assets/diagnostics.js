(() => {
  "use strict";

  const AUTH_URL = "https://manager.dmdata.jp/account/oauth2/v1/auth";
  const TOKEN_URL = "https://manager.dmdata.jp/account/oauth2/v1/token";
  const REVOKE_URL = "https://manager.dmdata.jp/account/oauth2/v1/revoke";
  const CONTRACT_URL = "https://api.dmdata.jp/v2/contract";
  const SCOPES = ["contract.list"];
  const OAUTH_SESSION_KEYS = [
    "qd_oauth_verifier",
    "qd_oauth_state",
    "qd_oauth_client_id",
    "qd_oauth_redirect_uri"
  ];

  const CAPABILITIES = Object.freeze([
    {
      id: "eew-forecast",
      classification: "eew.forecast",
      title: "Full EEW forecast updates",
      description: "Forecast-level early earthquake warnings, including updates for events that never become a public warning."
    },
    {
      id: "eew-warning",
      classification: "eew.warning",
      title: "Direct EEW warning feed",
      description: "DM-D.S.S warning telegrams delivered directly, with P2PQuake remaining available as QuakeDeck’s public-warning fallback."
    },
    {
      id: "eew-realtime",
      classification: "eew.realtime",
      title: "Realtime intensity / PLUM data",
      description: "Realtime intensity observations used by JMA’s PLUM method, available for future live-observation features."
    },
    {
      id: "earthquake-detail",
      classification: "telegram.earthquake",
      title: "Richer official earthquake reports",
      description: "JMA prefecture, region, city and station hierarchy, plus official revision states and additional report detail."
    },
    {
      id: "tsunami-observations",
      classification: "telegram.earthquake",
      title: "Observed tsunami data",
      description: "First-wave times and direction, measured maximum heights, tide-gauge stations, and offshore observations."
    },
    {
      id: "long-period-motion",
      classification: "telegram.earthquake",
      title: "Long-period ground motion",
      description: "JMA long-period ground-motion classes and observations that are especially relevant inside tall buildings."
    },
    {
      id: "earthquake-activity",
      classification: "telegram.earthquake",
      title: "Earthquake activity and official updates",
      description: "Felt-earthquake counts, activity reports, and revised hypocentre information for significant earthquakes."
    },
    {
      id: "special-advisories",
      classification: "telegram.earthquake",
      title: "Special earthquake advisories",
      description: "Nankai Trough information and Hokkaidō / Sanriku subsequent-earthquake advisories."
    }
  ]);

  const el = (id) => document.getElementById(id);
  const connectButton = el("connect-button");
  const clearButton = el("clear-button");
  const statusText = el("status-text");
  const statusDot = el("status-dot");
  const message = el("message");
  const results = el("results");
  const resultSummary = el("result-summary");
  const availableList = el("available-list");
  const unavailableList = el("unavailable-list");
  const jquakeNote = el("jquake-note");
  const downloadReportButton = el("download-report");

  let sanitizedReport = null;

  function configuredClientId() {
    return String(window.QUAKEDECK_CONFIG?.clientId || "").trim();
  }

  function clearOauthSession() {
    OAUTH_SESSION_KEYS.forEach((key) => sessionStorage.removeItem(key));
  }

  function currentRedirectUri() {
    return `${window.location.origin}${window.location.pathname}`;
  }

  function setStatus(kind, text) {
    statusText.textContent = text;
    statusDot.className = `status-dot${kind ? ` ${kind}` : ""}`;
  }

  function showMessage(kind, text) {
    message.className = `notice ${kind || "info"} connection-message`;
    message.textContent = text;
    message.classList.remove("hidden");
  }

  function hideMessage() {
    message.classList.add("hidden");
  }

  function base64Url(bytes) {
    let binary = "";
    bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  }

  function randomBase64Url(length = 48) {
    const bytes = new Uint8Array(length);
    crypto.getRandomValues(bytes);
    return base64Url(bytes);
  }

  async function sha256Base64Url(value) {
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
    return base64Url(new Uint8Array(digest));
  }

  async function readJsonResponse(response, context) {
    let body;
    try {
      body = await response.json();
    } catch {
      throw new Error(`${context} returned an unreadable response (HTTP ${response.status}).`);
    }
    if (!response.ok || body.status === "error" || body.error) {
      const detail = body?.error?.message || body?.error_description || body?.error || response.statusText;
      throw new Error(`${context} failed: ${detail} (HTTP ${response.status}).`);
    }
    return body;
  }

  async function readContracts(accessToken) {
    const response = await fetch(CONTRACT_URL, {
      headers: { Authorization: `Bearer ${accessToken}` },
      cache: "no-store"
    });
    return readJsonResponse(response, "DM-D.S.S plan check");
  }

  async function revokeToken(clientId, token) {
    if (!token) return true;
    const body = new URLSearchParams({ client_id: clientId, token });
    const response = await fetch(REVOKE_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body
    });
    return response.ok;
  }

  function cleanContract(item) {
    return {
      planName: item.planName ?? null,
      classification: item.classification ?? null,
      isValid: Boolean(item.isValid),
      connectionCounts: Number(item.connectionCounts || 0),
      start: item.start ?? null
    };
  }

  function buildReport(contractBody, grantedScope) {
    const contracts = (contractBody.items || []).map(cleanContract);
    const validContracts = contracts.filter((item) => item.isValid);
    const activeClassifications = [...new Set(
      validContracts.map((item) => item.classification).filter(Boolean)
    )].sort();
    const activeSet = new Set(activeClassifications);
    const capabilities = CAPABILITIES.map((capability) => ({
      ...capability,
      available: activeSet.has(capability.classification)
    }));
    const hasJquakeOnlyPlan = contracts.some((contract) => {
      const searchable = `${contract.planName || ""} ${contract.classification || ""}`.toLowerCase();
      return contract.isValid && searchable.includes("jquake");
    });

    return {
      generatedAt: new Date().toISOString(),
      checkVersion: "1.0",
      requestedScopes: SCOPES,
      grantedScopes: String(grantedScope || "").split(/\s+/).filter(Boolean),
      activeClassifications,
      capabilities,
      hasJquakeOnlyPlan,
      contracts
    };
  }

  function appendCapability(container, capability, available) {
    const item = document.createElement("div");
    item.className = "result-item capability-item";

    const copy = document.createElement("div");
    copy.className = "capability-copy";
    const title = document.createElement("strong");
    title.textContent = capability.title;
    const description = document.createElement("small");
    description.textContent = capability.description;
    copy.append(title, description);

    const badge = document.createElement("span");
    badge.className = `badge ${available ? "ok" : "off"}`;
    badge.textContent = available ? "Available" : "Not enabled";

    item.append(copy, badge);
    container.appendChild(item);
  }

  function renderReport(report, revokeSucceeded) {
    availableList.innerHTML = "";
    unavailableList.innerHTML = "";
    jquakeNote.classList.add("hidden");

    const available = report.capabilities.filter((capability) => capability.available);
    const unavailable = report.capabilities.filter((capability) => !capability.available);

    if (available.length === 0) {
      const empty = document.createElement("div");
      empty.className = "notice info";
      empty.textContent = "No DM-D.S.S data capability that QuakeDeck can use is active on this account. The P2PQuake foundation remains available.";
      availableList.appendChild(empty);
      resultSummary.textContent = "Your account does not currently add a QuakeDeck-compatible DM-D.S.S data feed. QuakeDeck’s normal P2PQuake features are unaffected.";
    } else {
      available.forEach((capability) => appendCapability(availableList, capability, true));
      const noun = available.length === 1 ? "capability" : "capabilities";
      resultSummary.textContent = `Your account provides ${available.length} DM-D.S.S data ${noun} that QuakeDeck can use.`;
    }

    unavailable.forEach((capability) => appendCapability(unavailableList, capability, false));

    if (report.hasJquakeOnlyPlan && !report.activeClassifications.includes("telegram.earthquake")) {
      jquakeNote.textContent = "A JQuake-only plan is visible, but it is not counted here because that access is restricted to JQuake and cannot be used by QuakeDeck.";
      jquakeNote.classList.remove("hidden");
    }

    results.classList.remove("hidden");
    if (revokeSucceeded) {
      setStatus("ok", "Access checked — disconnected");
      showMessage("success", "Your DM-D.S.S access was checked and the temporary authorization was revoked.");
    } else {
      setStatus("bad", "Access checked — revoke manually");
      showMessage("error", "Your result is ready, but automatic sign-out could not be confirmed. Revoke QuakeDeck from the DM-D.S.S control panel.");
    }
  }

  async function startAuthorization() {
    hideMessage();
    const clientId = configuredClientId();
    if (!clientId.startsWith("CId.")) {
      showMessage("error", "The DM-D.S.S connection is temporarily unavailable. Please try again after the site connection is configured.");
      return;
    }

    const redirectUri = currentRedirectUri();
    const verifier = randomBase64Url(64);
    const challenge = await sha256Base64Url(verifier);
    const state = randomBase64Url(32).slice(0, 64);

    sessionStorage.setItem("qd_oauth_verifier", verifier);
    sessionStorage.setItem("qd_oauth_state", state);
    sessionStorage.setItem("qd_oauth_client_id", clientId);
    sessionStorage.setItem("qd_oauth_redirect_uri", redirectUri);

    const params = new URLSearchParams({
      client_id: clientId,
      response_type: "code",
      redirect_uri: redirectUri,
      response_mode: "query",
      scope: SCOPES.join(" "),
      state,
      code_challenge: challenge,
      code_challenge_method: "S256"
    });

    setStatus("working", "Opening secure DM-D.S.S sign-in…");
    window.location.assign(`${AUTH_URL}?${params}`);
  }

  async function handleCallback(params) {
    const expectedState = sessionStorage.getItem("qd_oauth_state");
    const verifier = sessionStorage.getItem("qd_oauth_verifier");
    const clientId = sessionStorage.getItem("qd_oauth_client_id");
    const redirectUri = sessionStorage.getItem("qd_oauth_redirect_uri");
    const returnedState = params.get("state");
    const code = params.get("code");
    const oauthError = params.get("error");
    const oauthErrorDescription = params.get("error_description");

    history.replaceState({}, document.title, redirectUri || currentRedirectUri());

    if (oauthError) {
      clearOauthSession();
      throw new Error(`DM-D.S.S sign-in was not completed: ${oauthErrorDescription || oauthError}.`);
    }
    if (!code || !clientId || !verifier || !redirectUri || !expectedState) {
      clearOauthSession();
      throw new Error("This sign-in session is incomplete or expired. Please connect again in this browser tab.");
    }
    if (returnedState !== expectedState) {
      clearOauthSession();
      throw new Error("The sign-in response did not match this browser session, so it was rejected for safety.");
    }

    setStatus("working", "Finishing secure sign-in…");
    const tokenBody = new URLSearchParams({
      client_id: clientId,
      grant_type: "authorization_code",
      code,
      redirect_uri: redirectUri,
      code_verifier: verifier
    });
    const tokenResponse = await fetch(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: tokenBody
    });
    const token = await readJsonResponse(tokenResponse, "DM-D.S.S sign-in");

    let report = null;
    let revokeSucceeded = false;
    try {
      setStatus("working", "Checking your available data…");
      const contracts = await readContracts(token.access_token);
      report = buildReport(contracts, token.scope);
    } finally {
      setStatus("working", "Disconnecting safely…");
      const accessRevoked = await revokeToken(clientId, token.access_token).catch(() => false);
      const refreshRevoked = await revokeToken(clientId, token.refresh_token).catch(() => false);
      revokeSucceeded = accessRevoked && refreshRevoked;
      clearOauthSession();
    }

    sanitizedReport = report;
    renderReport(report, revokeSucceeded);
  }

  function clearResult() {
    clearOauthSession();
    sanitizedReport = null;
    results.classList.add("hidden");
    hideMessage();
    setStatus("", "Ready to connect");
  }

  function downloadReport() {
    if (!sanitizedReport) return;
    const blob = new Blob([JSON.stringify(sanitizedReport, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `quakedeck-dmdss-access-${new Date().toISOString().replaceAll(":", "-")}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  async function init() {
    connectButton.addEventListener("click", startAuthorization);
    clearButton.addEventListener("click", clearResult);
    downloadReportButton.addEventListener("click", downloadReport);

    if (!configuredClientId().startsWith("CId.")) {
      connectButton.disabled = true;
      setStatus("bad", "Connection setup pending");
      showMessage("info", "DM-D.S.S sign-in will be available here as soon as QuakeDeck’s public connection is registered.");
    }

    const params = new URLSearchParams(window.location.search);
    if (params.has("code") || params.has("error")) {
      connectButton.disabled = true;
      try {
        await handleCallback(params);
      } catch (error) {
        setStatus("bad", "Could not check access");
        showMessage("error", error instanceof Error ? error.message : String(error));
      } finally {
        connectButton.disabled = !configuredClientId().startsWith("CId.");
      }
    }
  }

  init().catch((error) => {
    setStatus("bad", "Could not start connection");
    showMessage("error", error instanceof Error ? error.message : String(error));
  });
})();
