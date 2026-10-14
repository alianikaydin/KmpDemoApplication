# Secrets

## Rule

No real secret goes into the repository: no API key, password, keystore, certificate, private key,
provisioning profile or token. `.gitignore` blocks the usual file types (`*.jks`, `*.keystore`,
`*.p12`, `*.pfx`, `*.p8`, `*.pem`, `*.key`, `*.mobileprovision`, `*.provisionprofile`,
`keystore.properties`, `.env`, `.env.*`; `.env.example` is allowed). Demo passwords of the in-memory
mock backend and the UI tests (`Demo1234` and similar) are not real accounts.

## Secret scan

The `secret-scan` job in `.github/workflows/ci.yml` runs [gitleaks](https://github.com/gitleaks/gitleaks)
on every push and pull request and fails on a finding. Make it a required check in the branch ruleset
so it blocks merging. To scan the whole history, run the CI workflow manually (`workflow_dispatch`).

Locally (any gitleaks 8.25.0 or newer):

```
gitleaks git --redact .   # history
gitleaks dir --redact .   # working tree
```

Configuration is `.gitleaks.toml`. An allowlist entry is only for a value the scan reported and that
is not a secret. Each entry needs a `description`, `condition = "AND"`, the exact file path and the
exact value. Never exclude a directory. If a finding is a real secret, rotate it first; deleting it
from the repository does not make it safe again.

## CI rules

- The workflow default is `permissions: contents: read`; a job asks for more only when it needs it.
- `GPR_READ_TOKEN` is set on the jobs that run Gradle (`build-and-test`, `web-build`, `ios-test`),
  not on the whole workflow and not on `secret-scan`. `ui-tests.yml` and
  `ios-session-storage.yml` still set secrets for the whole workflow.
- Actions are pinned by tag, not by commit SHA.
- Never print a secret in a step; GitHub masks registered secrets only when they are unchanged.

## Release signing

The release build reads the upload key from four Gradle properties. In CI they would be passed as
`ORG_GRADLE_PROJECT_<name>` from Actions secrets; CI does not build a release today.

| Property | Meaning |
|---|---|
| `kmpUploadStoreFile` | Absolute path of the keystore file, kept outside the repository |
| `kmpUploadStorePassword` | Keystore password |
| `kmpUploadKeyAlias` | Key alias |
| `kmpUploadKeyPassword` | Key password |

With none of them set, `release` is built unsigned. With only some set, the build fails and names the
missing properties (never their values). Keep the keystore and its passwords in a password manager or
an equivalent vault, and use Play App Signing: a lost upload key cannot be recovered by this project.

## Session storage

| Platform | Where the session is kept |
|---|---|
| Android | `session_secure.xml`, every value AES-256-GCM encrypted with a non-exportable Android Keystore key (alias `session_aes_gcm_v1`); excluded from cloud backup and device transfer |
| iOS | Keychain, `AfterFirstUnlockThisDeviceOnly` |
| Web (js, wasm) | `localStorage`, readable by any script on the page |

If a stored value cannot be decrypted, the whole session and the key are deleted and the user sees
Login. If a value cannot be encrypted, nothing is written; there is no plain-text fallback.

## Web token limits

- (a) `localStorage` is readable by JavaScript, so an XSS bug lets an attacker steal the tokens.
- (b) Encrypting in the browser does not help: the key would be available to the same JavaScript.
  The web build therefore stores tokens as they are and does not pretend otherwise.
- (c) The correct fix is to deliver the refresh token in an `httpOnly`, `Secure`, `SameSite` cookie.
  That needs a backend change (cookie handling, CORS, CSRF protection).
- (d) Until that separate story is done, the web app must not go to stage or prod with real users.
