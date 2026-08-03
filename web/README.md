# QuakeDeck website v0.1

A dependency-free static website and controlled DM-D.S.S OAuth capability probe.

## What the probe does

- Uses OAuth authorization-code flow with PKCE S256.
- Requests `contract.list`, `socket.list`, `socket.start`, `socket.close`,
  `telegram.get.earthquake`, and `eew.get.forecast`.
- Calls:
  - `GET https://api.dmdata.jp/v2/contract`
  - `GET https://api.dmdata.jp/v2/socket?limit=100`
  - `POST https://api.dmdata.jp/v2/socket` once to test the QuakeDeck stream entitlement
  - `DELETE https://api.dmdata.jp/v2/socket/{id}` if the test creates a socket
- Never opens the returned WebSocket. If Socket Start succeeds, immediately attempts
  to close the exact socket ID it created.
- Builds a sanitized report without tokens, client secrets, API keys, socket tickets, or IP addresses.
- Attempts to revoke both the temporary access token and refresh token after reading the report.

## Deploy with GitHub Pages

1. Create a public repository, for example `quakedeck-site`.
2. Upload the contents of this folder to the repository root.
3. Replace the placeholder in `contact.html` and optionally update `site-config.js`.
4. In GitHub: **Settings → Pages → Deploy from a branch → main / root**.
5. Wait for the HTTPS site URL, for example:
   `https://YOUR_NAME.github.io/quakedeck-site/`
6. Open `diagnostics.html` on the deployed site. It displays the exact redirect URI to register.

## Create the DM-D.S.S OAuth client

In the DM-D.S.S credentials/control panel create a client with:

- Client type: **Public**
- Flow: **Authorization Code**
- PKCE: used by the website (`S256`)
- Application URL: your deployed homepage
- Terms URL: `https://…/terms.html`
- Privacy URL: `https://…/privacy.html`
- Public contact: your real contact page or email
- Redirect URI: the exact value displayed on `diagnostics.html`
- Scopes for this probe only:
  - `contract.list`
  - `socket.list`
  - `socket.start`
  - `socket.close`
  - `telegram.get.earthquake`
  - `eew.get.forecast`

Paste the resulting public `CId.…` into the diagnostic page. A client ID is not a secret. Do not put a client secret, API key, access token, or refresh token into this site.

## Local preview

```powershell
python -m http.server 8080 --directory web
```

Open `http://127.0.0.1:8080/`.

For an OAuth localhost callback, DM-D.S.S documents special redirect matching for loopback HTTP addresses. The public application, terms, privacy, and contact pages are still needed for client registration.

## Later Android App Links

`.well-known/assetlinks.json.example` is a template only. Rename it to `assetlinks.json` after inserting the SHA-256 fingerprint of the final QuakeDeck **release** signing certificate. Do not use the debug certificate for the production association.

## Important

QuakeDeck is not an official warning service and must not be treated as a replacement for JMA alerts or public emergency instructions.
