# Contributing to Lumen Store

Thanks for helping. Bug reports, translations, documentation and code are all welcome.

## Ways to contribute

- Report bugs and request features through [Issues](https://github.com/deepcodecreate/lumenstore/issues/new/choose)
- Improve or add translations
- Fix bugs or build features from open issues
- Improve the docs and README

For larger changes, open an issue first so the approach can be agreed before you spend time on it.

## Development setup

Requirements: JDK 17 and the Android SDK (platform 36).

```bash
git clone https://github.com/deepcodecreate/lumenstore.git
cd lumenstore
./gradlew assembleDebug
```

Open the project in Android Studio to run it on a device or emulator.

## Pull requests

1. Fork the repository and create a branch from `main`
2. Keep each pull request focused on one change
3. Make sure `./gradlew assembleDebug` passes
4. Describe what changed and why, and attach screenshots for UI changes
5. Link the issue it closes

Follow the style of the surrounding code. New dependencies need a clear reason, and anything that adds tracking, analytics or ads will not be accepted.

## Commit messages

Use short, imperative messages with an optional prefix:

```
feat: add sort options to the update list
fix: crash when opening an app without a repository
docs: clarify build steps
```

## Translations

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`, for example `values-de`
2. Translate the text and leave names, placeholders such as `%1$s` and `%1$d`, and `translatable="false"` entries untouched
3. Copy `plurals_home.xml` the same way if you translate the plurals
4. Add the language to the language picker in `LanguageManager.java`
5. Open a pull request

Right-to-left languages are supported.

## Code of conduct

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

Contributions are released under the [GPL-3.0](LICENSE) license.
