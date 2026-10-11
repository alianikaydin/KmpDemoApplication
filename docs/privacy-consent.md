# Privacy and consent

Optional data collection (crash reports now, analytics later) is allowed only with the account's
consent. The decision belongs to the account, not to the device: it is stored by the backend and
the app keeps a copy in the session storage.

## The rule for every capability

Every capability that collects optional data asks the `ConsentManager` (`core/consent`) before it
sends anything, and collects only while the answer is `OptionalDataConsent.GRANTED`.

- The default is off. Before sign-in, while the decision is loading, when it could not be loaded
  and when the account has not decided yet, the answer is `UNKNOWN`: do not collect.
- `GRANTED` is the account's confirmed, current "granted". Switching on counts only after the
  backend confirmed it. Switching off counts at once, before the backend answers.
- `DENIED` means refused or withdrawn (on this or on another device).
- Sign-out and session expiry always give `UNKNOWN`, never `DENIED`, so a consumer can tell
  "the user said no" from "nobody is signed in". A change of account passes through `UNKNOWN` too.
- `optionalDataConsent` never throws, has a value at once (no suspending) and may be updated from
  any thread.

`ConsentManager` is bound to `AccountConsentManager` (`features/consent/domain`), which is created
when Koin starts. Tests use `FakeConsentManager` (`core/consent` in `commonTest`).

## Where the user decides

| Place | What happens |
|-------|--------------|
| Sign-up | An optional box, unchecked by default, never required. The choice is sent with the registration for the text that was shown (version and language). If the text could not be loaded, no decision is sent. |
| One-time prompt | After sign-in, once per session, when the account has no current decision (or a newer text asks again). "Accept" and "Reject" look the same. Leaving without answering asks again at the next sign-in. |
| Settings, Privacy | A switch. Switching off applies at once; if the backend cannot be reached the switch keeps showing the confirmed state, an error and a retry, and collection stays off. |

The consent text (label, description, privacy policy link) comes from the backend in the language
in use and is never part of the app's resources.

## Adding another category

The backend lists the purposes a text covers. When a category is added there, `OptionalDataConsent`
grows from one value to one question per category; consumers keep asking the same facade.

## Demo mode

The in-app mock backend serves the same consent API. `demo@example.com` is seeded with a granted
decision, `consent@example.com` / `Demo1234` has no decision yet and sees the prompt. Decisions live
in memory and are lost when the process ends.
