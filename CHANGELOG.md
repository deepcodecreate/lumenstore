# Changelog

All notable changes are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.5.2] - Beta

### Changed
- GitHub account page redesigned to be minimal and compact: smaller avatar and header without the oversized card, one slim stats row, a single-row action bar, tighter repository, language, people and activity items

## [0.5.1] - Beta

### Fixed
- Lumen Store is now detected in the Home list so its own updates appear
- Manual repositories showing "None found" during a scan: the saved list is recovered automatically

### Changed
- R8 shrinking turned off again until fully tested on devices
- Releases now include both release and debug APKs with checksums

## [0.5.0] - Beta

### Added
- Scan installed apps and match them against F-Droid, IzzyOnDroid and GitHub releases
- One-tap install and update with ABI-aware APK selection
- GitHub explorer with README rendering, screenshots and profiles
- Custom repositories from GitHub, GitLab and Codeberg
- Google Play search
- Backup and restore, with optional automatic backups
- Material 3 themes, seed colors, icon shapes and live backgrounds
- English and Persian with RTL support
- `lumenstore://app/<package-name>` deep links
- GitHub Actions for builds, releases, CodeQL and dependency tracking

### Security
- GitHub token is encrypted with a hardware-backed Android Keystore key and migrated from plain storage
- System backups are disabled so the token never leaves the device
- File sharing paths restricted to app-private and app download folders
