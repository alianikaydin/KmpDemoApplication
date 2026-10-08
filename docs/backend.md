# Backend

The backend lives in its own repository,
[`alianikaydin/KmpDemoBackend`](https://github.com/alianikaydin/KmpDemoBackend)
(Kotlin, Ktor, PostgreSQL). Its README explains how to start the server; this
page covers what the app needs to build against it and to talk to it.

## Contract library (needed to build)

The auth DTOs, route paths and credential rules come from the published
library `com.anksoft.kmpdemo:contract` on GitHub Packages. Gradle needs a token
with `read:packages` to download it, so **every build needs credentials**, also
when you only use demo mode.

Put them in `~/.gradle/gradle.properties` (never in the repo):

```
kmpDemoBackendUsername=<your GitHub user name>
kmpDemoBackendPassword=<classic personal access token with read:packages>
```

In CI they come from the `GPR_READ_TOKEN` Actions secret
(`ORG_GRADLE_PROJECT_kmpDemoBackendUsername` / `...Password` in the workflows).
iOS builds started from Xcode call Gradle, which reads the same file.

A `401` from `maven.pkg.github.com` means the token is missing, expired or
lacks `read:packages`. Check the token first.

## Start the backend

```
git clone https://github.com/alianikaydin/KmpDemoBackend
cd KmpDemoBackend
scripts/server-up.sh        # needs JDK 21, Docker with compose, openssl, curl
```

The server then answers at `http://localhost:8081/api/v1/` (health:
`/health/ready`). Stop it with `scripts/server-down.sh` (`--volumes` also
deletes the data). The local Docker setup allows the web origins
`http://localhost:8080` and `http://127.0.0.1:8080` through CORS.

## Running the apps against the backend

Without a backend URL, the dev environment runs in demo mode on mobile (in-app
mock server, user `demo@example.com` / `Demo1234`); on web demo mode needs
`-PkmpDemo=true`. Stage and prod never use the mock or a custom URL. See
`docs/environments.md`. A missing
trailing `/` on the URL is added automatically.

| Platform | Base URL | How to set it |
|----------|----------|---------------|
| Android emulator | `http://10.0.2.2:8081/api/v1/` | `-PkmpBackendUrl=...` |
| Android device over USB | `http://localhost:8081/api/v1/` | `adb reverse tcp:8081 tcp:8081`, then the same property |
| iOS simulator | `http://localhost:8081/api/v1/` | `iosApp/Configuration/Local.xcconfig` |
| Web | `http://localhost:8081/api/v1/` | `-PkmpBackendUrl=...` |

Android:

```
./gradlew :androidApp:installDevDebug -PkmpBackendUrl=http://10.0.2.2:8081/api/v1/
```

iOS: copy `iosApp/Configuration/Local.xcconfig.example` to
`iosApp/Configuration/Local.xcconfig` (git-ignored) and build the `iosApp` scheme (Debug, dev) in Xcode. In
xcconfig files `//` starts a comment, so the example writes the URL as
`http:/$()/localhost:8081/api/v1/`.

Web:

```
./gradlew :webApp:wasmJsBrowserDevelopmentRun -PkmpBackendUrl=http://localhost:8081/api/v1/
```

Or, without a backend, `-PkmpDemo=true` instead of the URL.

Open the page at `http://localhost:8080` (or `127.0.0.1:8080`); any other
origin is blocked by the backend's CORS list.

Cleartext HTTP:

- Android: only in debug builds, and only to `10.0.2.2` and `localhost` (debug
  network security config).
- iOS: `NSAllowsLocalNetworking` is set in every configuration on purpose; it
  permits local-network hosts such as `localhost` and `*.local`.

iOS physical device: the phone can reach the Mac as `http://<mac-name>.local:8081/api/v1/`
(set it in `Local.xcconfig`). The backend's `compose.yaml` binds the port to
`127.0.0.1` only, so its port binding must be changed to allow LAN access
first. Android physical devices should use `adb reverse` as above.

## Sessions and token refresh

The access token is short-lived. When a protected request gets `401`, the client
sends the stored refresh token to `auth/refresh` once, saves the new token pair
and repeats the request. Several requests failing together share one refresh,
because a refresh token may be used only once.

- A `401` from `auth/login`, `auth/register` or `auth/refresh` never starts a
  refresh (wrong password, rejected refresh token).
- If `auth/refresh` answers `401` (revoked, expired or reused token), the
  session is cleared, the app returns to Login and shows "Your session
  expired". Network errors and `5xx` keep the session.
- The backend must send `WWW-Authenticate: Bearer` on its `401` answers; the
  client's auth plugin starts a refresh only when it sees that header.
- Logging in, registering and logging out clear the client's cached bearer
  token, so a request after an account switch never carries the old account's
  token.

The demo mock server follows the same rules: `auth/refresh` accepts each refresh
token once, and every `401` carries `WWW-Authenticate: Bearer`.
