# Changelog

What changes for the users of Micro Review for AnkiDroid, one line per visible change, in plain words: these lines
become the release notes of the GitHub release and of the Play Store (500 characters max). Add yours under
**Unreleased** in the pull request that makes the change; the final release turns that section into the version's.
Internal changes (tests, build, documentation) don't go here: the GitHub release lists every pull request.

## Unreleased

- Settings are tidier: folded sections showing their current values, all lock screen options together, and a search bar.
- Cards keep their bold, italics and underlining, and the cloze answer is shown in bold.
- When the only cards left have sound or just a picture in the question, the main screen says so instead of "Bravo".
- The main screen shows how many cards you reviewed today, and for how many days in a row.
- Without AnkiDroid, the app now offers to install it; with its API turned off, it opens AnkiDroid to turn it on.
- On cards whose answer repeated the question, only the answer is shown now.
- The card notification can be swiped away like other notifications. It comes back at the next lock, or with the next card.
- Decks added in AnkiDroid, and their due counts, now show up when you come back to the app, without restarting it.

## 0.5.0 (2026-11-03)

- Long cards: read them page by page with Previous / Next in the notification. The answer buttons always stay visible.
- Long cards scroll in the home screen widget.
- New setting: how many cards to review per unlock (1 to 10, or no limit).
- The notification and the review screen close by themselves after your last card.
- The card on the lock screen now sits below your notifications, so they no longer cover it.

## 0.4.0 (2026-10-27)

- First release on Google Play: review your AnkiDroid cards from the lock screen, a notification or a home screen
  widget.
