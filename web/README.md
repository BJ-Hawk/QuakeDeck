# QuakeDeck website

A dependency-free static website with a user-facing DM-D.S.S OAuth access check.

## What the access check does

- Uses the OAuth authorization-code flow with PKCE S256.
- Requests only the read-only `contract.list` permission.
- Calls `GET https://api.dmdata.jp/v2/contract` once.
- Maps active DM-D.S.S classifications to plain-language QuakeDeck capabilities.
- Does not request socket permissions, create a socket, or open a WebSocket.
- Does not store OAuth tokens or send them to a QuakeDeck server.
- Attempts to revoke the temporary access and refresh tokens immediately after the check.
- Can download a privacy-safe summary without tokens, secrets, tickets, or IP addresses.

## Configure the public OAuth client

In the DM-D.S.S credentials/control panel, create a client with:

- Client type: **Public**
- Flow: **Authorization Code**
- PKCE: **S256**
- Application URL: `https://bj-hawk.github.io/QuakeDeck/`
- Terms URL: `https://bj-hawk.github.io/QuakeDeck/terms.html`
- Privacy URL: `https://bj-hawk.github.io/QuakeDeck/privacy.html`
- Public contact: `https://github.com/BJ-Hawk/QuakeDeck/issues`
- Redirect URI: `https://bj-hawk.github.io/QuakeDeck/diagnostics.html`
- Scope: `contract.list`

Put the resulting public `CId.…` value in `site-config.js`. A public client ID is safe to ship in browser code. Never put a client secret, API key, access token, or refresh token in this site.

## Deploy with GitHub Pages

The repository workflow publishes only the contents of `/web`.

1. In repository **Settings → Pages**, select **GitHub Actions** as the source.
2. Push a change under `web/**`, or run **Deploy QuakeDeck website** manually.
3. Open `https://bj-hawk.github.io/QuakeDeck/diagnostics.html`.

The OAuth redirect URI must match the value registered with DM-D.S.S exactly.

## Local preview

```powershell
python -m http.server 8080 --directory web
```

Open `http://127.0.0.1:8080/`. The user-facing page will remain safely disabled until a valid public client ID is configured. DM-D.S.S documents special redirect matching for loopback HTTP addresses if local OAuth testing is required.

## Later Android App Links

`.well-known/assetlinks.json.example` is a template only. Rename it to `assetlinks.json` after inserting the SHA-256 fingerprint of the final QuakeDeck **release** signing certificate. Do not use the debug certificate for the production association.

## Public pages

- `about.html` — project purpose, inspiration, current scope, and direction
- `credits.html` — data-source attributions, licences, service terms, and review notes
- `contact.html` — GitHub repository, issue tracker, and current GitHub account address
- `diagnostics.html` — user-facing DM-D.S.S access and capability summary

## Important

QuakeDeck is not an official warning service and must not be treated as a replacement for JMA alerts or public emergency instructions.
