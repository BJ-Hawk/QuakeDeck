(() => {
  "use strict";

  const AUTH_URL = "https://manager.dmdata.jp/account/oauth2/v1/auth";
  const TOKEN_URL = "https://manager.dmdata.jp/account/oauth2/v1/token";
  const REVOKE_URL = "https://manager.dmdata.jp/account/oauth2/v1/revoke";
  const CONTRACT_URL = "https://api.dmdata.jp/v2/contract";
  const SOCKET_LIST_URL = "https://api.dmdata.jp/v2/socket?limit=100";
  const SOCKET_START_URL = "https://api.dmdata.jp/v2/socket";
  const SCOPES = [
    "contract.list",
    "socket.list",
    "socket.start",
    "socket.close",
    "telegram.get.earthquake",
    "eew.get.forecast"
  ];
  const OAUTH_SESSION_KEYS = [
    "qd_oauth_verifier",
    "qd_oauth_state",
    "qd_oauth_client_id",
    "qd_oauth_redirect_uri"
  ];
  const SOCKET_TEST_REQUEST = Object.freeze({
    classifications: ["telegram.earthquake", "eew.forecast"],
    types: ["VXSE51", "VXSE52", "VXSE53", "VXSE61", "VTSE41", "VXSE43", "VXSE45"],
    test: "no",
    appName: "QuakeDeck-Probe",
    formatMode: "raw"
  });

  const el = (id) => document.getElementById(id);
  const clientIdInput = el("client-id");
  const redirectInput = el("redirect-uri");
  const connectButton = el("connect-button");
  const clearButton = el("clear-button");
  const statusText = el("status-text");
  const statusDot = el("status-dot");
  const message = el("message");
  const results = el("results");
  const contractsList = el("contracts-list");
  const socketsList = el("sockets-list");
  const interpretationList = el("interpretation-list");
  const socketTestList = el("socket-test-list");
  const summaryPre = el("summary-json");
  const copyRedirectButton = el("copy-redirect");
  const copyReportButton = el("copy-report");
  const downloadReportButton = el("download-report");

  let sanitizedReport = null;

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
    message.className = `notice ${kind || "info"}`;
    message.textContent = text;
    message.classList.remove("hidden");
  }

  function hideMessage() {
    message.classList.add("hidden");
  }

  function base64Url(bytes) {
    let binary = "";
    bytes.forEach((b) => { binary += String.fromCharCode(b); });
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
      throw new Error(`${context} returned HTTP ${response.status} with a non-JSON body.`);
    }
    if (!response.ok || body.status === "error" || body.error) {
      const detail = body?.error?.message || body?.error_description || body?.error || response.statusText;
      throw new Error(`${context} failed: ${detail} (HTTP ${response.status}).`);
    }
    return body;
  }

  async function apiGet(url, accessToken, context) {
    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${accessToken}` },
      cache: "no-store"
    });
    return readJsonResponse(response, context);
  }

  async function readOptionalJson(response) {
    const text = await response.text();
    if (!text) return null;
    try {
      return JSON.parse(text);
    } catch {
      return { nonJsonBody: true };
    }
  }

  function apiErrorDetails(response, body) {
    return {
      httpStatus: response.status,
      errorCode: body?.error?.code ?? response.status,
      errorMessage: body?.error?.message || body?.error_description || body?.error || response.statusText || null
    };
  }

  async function closeCreatedSocket(socketId, accessToken) {
    const response = await fetch(`${SOCKET_START_URL}/${encodeURIComponent(socketId)}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${accessToken}` },
      cache: "no-store"
    });
    const body = await readOptionalJson(response);
    const details = apiErrorDetails(response, body);
    return {
      attempted: true,
      succeeded: response.ok,
      httpStatus: details.httpStatus,
      errorCode: response.ok ? null : details.errorCode,
      errorMessage: response.ok ? null : details.errorMessage
    };
  }

  async function runSocketEntitlementTest(accessToken) {
    const request = {
      classifications: [...SOCKET_TEST_REQUEST.classifications],
      types: [...SOCKET_TEST_REQUEST.types],
      test: SOCKET_TEST_REQUEST.test,
      appName: SOCKET_TEST_REQUEST.appName,
      formatMode: SOCKET_TEST_REQUEST.formatMode
    };

    let response;
    try {
      response = await fetch(SOCKET_START_URL, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(request),
        cache: "no-store"
      });
    } catch (error) {
      return {
        attemptedAt: new Date().toISOString(),
        request,
        outcome: "network_error",
        httpStatus: null,
        errorCode: null,
        errorMessage: error instanceof Error ? error.message : String(error),
        interpretation: "The browser could not reach Socket Start, so entitlement was not tested.",
        cleanup: { attempted: false, succeeded: null, httpStatus: null, errorCode: null, errorMessage: null }
      };
    }

    const body = await readOptionalJson(response);
    const details = apiErrorDetails(response, body);
    const result = {
      attemptedAt: new Date().toISOString(),
      request,
      outcome: "unknown_error",
      httpStatus: details.httpStatus,
      errorCode: response.ok ? null : details.errorCode,
      errorMessage: response.ok ? null : details.errorMessage,
      interpretation: "DM-D.S.S returned an unrecognized result.",
      cleanup: { attempted: false, succeeded: null, httpStatus: null, errorCode: null, errorMessage: null }
    };

    if (response.ok && body?.status === "ok") {
      const socketId = body?.websocket?.id ?? null;
      result.createdSocketId = socketId;
      result.returnedClassifications = Array.isArray(body.classifications) ? body.classifications : [];
      result.returnedTypes = Array.isArray(body.types) ? body.types : null;
      result.returnedFormats = Array.isArray(body.formats) ? body.formats : [];
      result.returnedAppName = body.appName ?? null;

      if (socketId === null) {
        result.outcome = "authorized_cleanup_impossible";
        result.interpretation = "Socket Start succeeded, proving access, but no socket ID was returned for immediate cleanup. Check the DM-D.S.S control panel for QuakeDeck-Probe.";
        result.cleanup.errorMessage = "No socket ID was returned.";
        return result;
      }

      try {
        result.cleanup = await closeCreatedSocket(socketId, accessToken);
      } catch (error) {
        result.cleanup = {
          attempted: true,
          succeeded: false,
          httpStatus: null,
          errorCode: null,
          errorMessage: error instanceof Error ? error.message : String(error)
        };
      }

      if (result.cleanup.succeeded) {
        result.outcome = "authorized_socket_created_and_closed";
        result.interpretation = "The QuakeDeck OAuth client was allowed to create the requested earthquake + EEW socket. The probe never connected to it and immediately closed the exact socket ID returned by DM-D.S.S.";
      } else {
        result.outcome = "authorized_cleanup_failed";
        result.interpretation = "The QuakeDeck OAuth client was allowed to create the requested earthquake + EEW socket, but automatic Socket Close failed. Close QuakeDeck-Probe manually in the DM-D.S.S control panel.";
      }
      return result;
    }

    if (response.status === 409 || details.errorCode === 409) {
      result.outcome = "connection_limit_full";
      result.interpretation = "DM-D.S.S returned the simultaneous-connection limit while JQuake occupied the only slot. This is strong evidence that the requested earthquake + EEW socket is usable by the QuakeDeck OAuth client, although the API does not document validation order.";
      return result;
    }

    if (response.status === 402 || details.errorCode === 402) {
      result.outcome = "no_contract";
      result.interpretation = "DM-D.S.S says this OAuth client has no contract usable for the requested earthquake + EEW socket. The JQuake-exclusive entitlement may be restricted to JQuake.";
      return result;
    }

    if (response.status === 403 || details.errorCode === 403) {
      result.outcome = "insufficient_scope_or_restricted_client";
      result.interpretation = "The request was forbidden. Check that all six probe scopes are enabled and granted; if they are, the JQuake-exclusive entitlement may be restricted to JQuake's client.";
      return result;
    }

    if (response.status === 400 || details.errorCode === 400) {
      result.outcome = "invalid_socket_request";
      result.interpretation = "DM-D.S.S rejected the socket request itself. The error text should show which requested field or data type it disliked.";
      return result;
    }

    return result;
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
      planId: item.planId ?? null,
      classification: item.classification ?? null,
      isValid: Boolean(item.isValid),
      connectionCounts: Number(item.connectionCounts || 0),
      start: item.start ?? null
    };
  }

  function cleanSocket(item) {
    return {
      id: item.id ?? null,
      status: item.status ?? null,
      appName: item.appName ?? null,
      classifications: Array.isArray(item.classifications) ? item.classifications : [],
      types: Array.isArray(item.types) ? item.types : null,
      formats: Array.isArray(item.formats) ? item.formats : [],
      test: item.test ?? null,
      start: item.start ?? null,
      end: item.end ?? null,
      ping: item.ping ?? null,
      server: item.server ?? null
    };
  }

  function classifyMeaning(classification) {
    switch (classification) {
      case "telegram.earthquake": return "Detailed JMA earthquake and tsunami telegrams";
      case "eew.forecast": return "Full EEW forecast stream";
      case "eew.warning": return "EEW warning-only stream";
      case "eew.realtime": return "Realtime intensity / PLUM-related stream";
      default: return "Other DM-D.S.S classification";
    }
  }

  function buildReport(contractBody, socketBody, grantedScope, socketEntitlementTest) {
    const contracts = (contractBody.items || []).map(cleanContract);
    const sockets = (socketBody.items || []).map(cleanSocket);
    const validContracts = contracts.filter((item) => item.isValid);
    const activeClassifications = [...new Set(validContracts.map((item) => item.classification).filter(Boolean))].sort();
    const totalConnectionAllowance = validContracts.reduce((sum, item) => sum + item.connectionCounts, 0);
    const openSockets = sockets.filter((item) => item.status === "open");

    return {
      generatedAt: new Date().toISOString(),
      probeVersion: "0.2",
      requestedScopes: SCOPES,
      grantedScopes: String(grantedScope || "").split(/\s+/).filter(Boolean),
      capabilitySummary: {
        activeClassifications,
        totalConnectionAllowance,
        openSocketCount: openSockets.length,
        freeConnectionSlotsEstimate: Math.max(0, totalConnectionAllowance - openSockets.length),
        note: "The free-slot value is an estimate based on valid contract connection counts and currently open sockets."
      },
      socketEntitlementTest,
      contracts,
      sockets
    };
  }

  function renderReport(report) {
    contractsList.innerHTML = "";
    socketsList.innerHTML = "";
    interpretationList.innerHTML = "";
    socketTestList.innerHTML = "";

    if (report.contracts.length === 0) {
      contractsList.innerHTML = '<div class="notice info">No contract entries were returned.</div>';
    } else {
      report.contracts.forEach((contract) => {
        const item = document.createElement("div");
        item.className = "result-item";
        item.innerHTML = `
          <div>
            <strong>${escapeHtml(contract.planName || "Unnamed plan")}</strong>
            <small>${escapeHtml(contract.classification || "No classification")} · ${escapeHtml(classifyMeaning(contract.classification))}</small>
          </div>
          <span class="badge ${contract.isValid ? "ok" : "off"}">${contract.isValid ? "Active" : "Inactive"} · +${contract.connectionCounts}</span>
        `;
        contractsList.appendChild(item);
      });
    }

    if (report.sockets.length === 0) {
      socketsList.innerHTML = '<div class="notice info">No sockets were returned.</div>';
    } else {
      report.sockets.forEach((socket) => {
        const item = document.createElement("div");
        item.className = "result-item";
        const badgeClass = socket.status === "open" ? "busy" : "off";
        const appName = socket.appName || "Unnamed application";
        item.innerHTML = `
          <div>
            <strong>${escapeHtml(appName)}</strong>
            <small>${escapeHtml((socket.classifications || []).join(", ") || "No classifications")} · started ${escapeHtml(formatTime(socket.start))}</small>
          </div>
          <span class="badge ${badgeClass}">${escapeHtml(socket.status || "unknown")}</span>
        `;
        socketsList.appendChild(item);
      });
    }

    const active = report.capabilitySummary.activeClassifications;
    const capabilityRows = [
      ["Detailed earthquake reports", active.includes("telegram.earthquake")],
      ["Full EEW forecasts", active.includes("eew.forecast")],
      ["Warning-only EEW", active.includes("eew.warning")],
      ["Realtime intensity / PLUM", active.includes("eew.realtime")]
    ];
    capabilityRows.forEach(([label, available]) => {
      const item = document.createElement("div");
      item.className = "result-item";
      item.innerHTML = `<strong>${escapeHtml(label)}</strong><span class="badge ${available ? "ok" : "off"}">${available ? "Contract visible" : "Not visible"}</span>`;
      interpretationList.appendChild(item);
    });

    const connectionItem = document.createElement("div");
    connectionItem.className = "result-item";
    connectionItem.innerHTML = `
      <div>
        <strong>WebSocket capacity</strong>
        <small>${report.capabilitySummary.openSocketCount} open now; estimate excludes waiting/stale edge cases.</small>
      </div>
      <span class="badge ${report.capabilitySummary.freeConnectionSlotsEstimate > 0 ? "ok" : "busy"}">${report.capabilitySummary.freeConnectionSlotsEstimate} free of ${report.capabilitySummary.totalConnectionAllowance}</span>
    `;
    interpretationList.appendChild(connectionItem);

    const test = report.socketEntitlementTest;
    const outcomeLabels = {
      connection_limit_full: ["409 · slot full", "busy"],
      authorized_socket_created_and_closed: ["Authorized · cleaned up", "ok"],
      authorized_cleanup_failed: ["Authorized · cleanup failed", "busy"],
      authorized_cleanup_impossible: ["Authorized · check control panel", "busy"],
      no_contract: ["402 · no contract", "off"],
      insufficient_scope_or_restricted_client: ["403 · forbidden", "off"],
      invalid_socket_request: ["400 · invalid request", "off"],
      network_error: ["Network error", "off"],
      unknown_error: [`HTTP ${test?.httpStatus ?? "?"}`, "off"]
    };
    const [testLabel, testClass] = outcomeLabels[test?.outcome] || outcomeLabels.unknown_error;

    const resultItem = document.createElement("div");
    resultItem.className = "result-item";
    resultItem.innerHTML = `
      <div>
        <strong>Socket Start result</strong>
        <small>${escapeHtml(test?.interpretation || "No socket test result was recorded.")}</small>
      </div>
      <span class="badge ${testClass}">${escapeHtml(testLabel)}</span>
    `;
    socketTestList.appendChild(resultItem);

    const requestItem = document.createElement("div");
    requestItem.className = "result-item";
    requestItem.innerHTML = `
      <div>
        <strong>Requested stream</strong>
        <small>${escapeHtml((test?.request?.classifications || []).join(", "))}<br>${escapeHtml((test?.request?.types || []).join(", "))}</small>
      </div>
      <span class="badge off">${escapeHtml(test?.request?.formatMode || "raw")}</span>
    `;
    socketTestList.appendChild(requestItem);

    if (test?.errorMessage) {
      const errorItem = document.createElement("div");
      errorItem.className = "result-item";
      errorItem.innerHTML = `
        <div>
          <strong>DM-D.S.S response</strong>
          <small>${escapeHtml(test.errorMessage)}</small>
        </div>
        <span class="badge off">${escapeHtml(test.errorCode ?? test.httpStatus ?? "error")}</span>
      `;
      socketTestList.appendChild(errorItem);
    }

    if (test?.cleanup?.attempted) {
      const cleanupItem = document.createElement("div");
      cleanupItem.className = "result-item";
      cleanupItem.innerHTML = `
        <div>
          <strong>Automatic cleanup</strong>
          <small>${test.cleanup.succeeded
            ? "The exact socket returned by Socket Start was closed before OAuth tokens were revoked."
            : escapeHtml(test.cleanup.errorMessage || "Socket Close could not be confirmed. Check the DM-D.S.S control panel.")}</small>
        </div>
        <span class="badge ${test.cleanup.succeeded ? "ok" : "busy"}">${test.cleanup.succeeded ? "Closed" : "Check manually"}</span>
      `;
      socketTestList.appendChild(cleanupItem);
    }

    summaryPre.textContent = JSON.stringify(report, null, 2);
    results.classList.remove("hidden");
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function formatTime(value) {
    if (!value) return "unknown time";
    const date = new Date(value);
    return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
  }

  async function startAuthorization() {
    hideMessage();
    const clientId = clientIdInput.value.trim();
    if (!clientId.startsWith("CId.")) {
      showMessage("error", "Enter the public DM-D.S.S OAuth client ID. It should begin with CId.");
      clientIdInput.focus();
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
    localStorage.setItem("qd_public_client_id", clientId);

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

    setStatus("working", "Opening DM-D.S.S authorization…");
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
      throw new Error(`Authorization failed: ${oauthErrorDescription || oauthError}.`);
    }
    if (!code || !clientId || !verifier || !redirectUri || !expectedState) {
      throw new Error("The OAuth callback is missing its saved PKCE session. Start the connection again in this browser tab.");
    }
    if (returnedState !== expectedState) {
      throw new Error("OAuth state mismatch. The request was rejected for safety.");
    }

    setStatus("working", "Exchanging the authorization code…");
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
    const token = await readJsonResponse(tokenResponse, "Token exchange");

    let revokeSucceeded = true;
    try {
      setStatus("working", "Reading contracts and socket status…");
      const [contracts, sockets] = await Promise.all([
        apiGet(CONTRACT_URL, token.access_token, "Contract list"),
        apiGet(SOCKET_LIST_URL, token.access_token, "Socket list")
      ]);

      setStatus("working", "Testing the QuakeDeck earthquake + EEW socket entitlement…");
      const socketEntitlementTest = await runSocketEntitlementTest(token.access_token);
      sanitizedReport = buildReport(contracts, sockets, token.scope, socketEntitlementTest);
      renderReport(sanitizedReport);
    } finally {
      setStatus("working", "Revoking the temporary diagnostic authorization…");
      const accessRevoked = await revokeToken(clientId, token.access_token).catch(() => false);
      const refreshRevoked = await revokeToken(clientId, token.refresh_token).catch(() => false);
      revokeSucceeded = accessRevoked && refreshRevoked;
      clearOauthSession();
    }

    setStatus("ok", "Capability report complete");
    if (revokeSucceeded) {
      const cleanupFailed = sanitizedReport?.socketEntitlementTest?.outcome === "authorized_cleanup_failed"
        || sanitizedReport?.socketEntitlementTest?.outcome === "authorized_cleanup_impossible";
      if (cleanupFailed) {
        showMessage("error", "The diagnostic completed and OAuth tokens were revoked, but the temporary QuakeDeck-Probe socket could not be confirmed closed. Close it manually in the DM-D.S.S control panel.");
      } else {
        showMessage("success", "The diagnostic completed. No WebSocket connection was opened, any socket ticket created was immediately closed, and the temporary OAuth tokens were revoked.");
      }
    } else {
      showMessage("error", "The report was read, but automatic token revocation could not be confirmed. Revoke QuakeDeck Capability Probe from the DM-D.S.S control panel before continuing.");
    }
  }

  function clearLocalData() {
    localStorage.removeItem("qd_public_client_id");
    clearOauthSession();
    clientIdInput.value = window.QUAKEDECK_CONFIG?.clientId || "";
    sanitizedReport = null;
    results.classList.add("hidden");
    hideMessage();
    setStatus("", "Not connected");
  }

  async function copyText(text, successText) {
    try {
      await navigator.clipboard.writeText(text);
      showMessage("success", successText);
    } catch {
      showMessage("error", "Clipboard access was blocked. Select and copy the value manually.");
    }
  }

  function downloadReport() {
    if (!sanitizedReport) return;
    const blob = new Blob([JSON.stringify(sanitizedReport, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `quakedeck-dmdss-capabilities-${new Date().toISOString().replaceAll(":", "-")}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  async function init() {
    redirectInput.value = currentRedirectUri();
    clientIdInput.value = localStorage.getItem("qd_public_client_id") || window.QUAKEDECK_CONFIG?.clientId || "";

    connectButton.addEventListener("click", startAuthorization);
    clearButton.addEventListener("click", clearLocalData);
    copyRedirectButton.addEventListener("click", () => copyText(redirectInput.value, "Redirect URI copied."));
    copyReportButton.addEventListener("click", () => sanitizedReport && copyText(JSON.stringify(sanitizedReport, null, 2), "Sanitized report copied. It contains no OAuth tokens, socket tickets, or IP addresses."));
    downloadReportButton.addEventListener("click", downloadReport);

    const params = new URLSearchParams(window.location.search);
    if (params.has("code") || params.has("error")) {
      connectButton.disabled = true;
      try {
        await handleCallback(params);
      } catch (error) {
        setStatus("bad", "Capability probe failed");
        showMessage("error", error instanceof Error ? error.message : String(error));
      } finally {
        connectButton.disabled = false;
      }
    }
  }

  init().catch((error) => {
    setStatus("bad", "Initialization failed");
    showMessage("error", error instanceof Error ? error.message : String(error));
  });
})();
