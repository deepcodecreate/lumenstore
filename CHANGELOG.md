# Changelog

All notable changes are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.6.2-beta]

### Added
- Manual repository management: a new Manage releases screen lists every release tag and APK asset of a GitHub repository
- Per repository you can choose the release channel (auto, stable, pre-release, all), limit updates to tags containing a word such as rc or a regular expression, set the version updates are checked from, pick the preferred APK by name with wildcards, and tell the app which tag your installed version is
- A repository can be linked to a specific installed app when its package could not be detected

### Changed
- Several downloaded apps are now installed one after another: the next install dialog appears only after the previous install finishes, is cancelled or fails
- App detail buttons now show Installing... or Updating... for the whole install and no longer flash back to Install or Update while the system is still installing
- After an install completes the detail page switches straight to the Open and Uninstall buttons
- Cancelled installs are detected from the system install session instead of guessing, and tapping install again while the confirmation is pending reopens it

### Fixed
- Installed apps whose package differs from the repository only by a suffix such as debug, beta, nightly or foss are now recognised and shown as installed instead of missing
- Pre-release update memory no longer overrides a release channel chosen by the user

## [0.6.1-beta]

### Changed
- Loading placeholders in app lists now share a single animation clock and cached theme colors, which removes per-item animators and theme lookups while scrolling
- Smoother list binding: each row now updates its text once instead of twice

## [0.6.0-beta]

### Changed
- New version comparison engine: pre-release steps such as v5.0 rc0 to v5.0rc1 are detected, numbers are compared safely, and words like src or device are no longer mistaken for pre-release markers
- GitHub releases are now chosen by the highest compatible version instead of listing order, so backport releases and overlapping tags no longer hide the real latest version
- Auto-matched GitHub apps now follow the same stable or pre-release track as the installed version
- The installed release tag is now recorded from the download link, so updates are detected even when the APK name has no version and the version name does not change
- GitHub releases referenced by indexes are resolved in parallel for much faster list and update refreshes

## [0.5.10-beta]

### Changed
- The Explore Lumen apps slider changes slides sooner and moves with a single smooth, cleaner animation; transitions, page dots and the progress fill share one timing
- Each anti-feature in the app detail now shows a short description in the app language

## [0.5.9-beta]

### Fixed
- The Explore Lumen apps slider now advances automatically on right-to-left layouts too
- Swiping the slider by hand no longer squeezes the banner into a clipped square shape; the transition is now flat and clean

### Changed
- Each anti-feature in the app detail now shows a short description in both Persian and English

## [0.5.8-beta]

### Fixed
- The Home add-button menu no longer cuts the top item while opening and no longer mixes up when the list is scrolled quickly
- The add button could stay hidden after quick direction changes while scrolling; it now always follows the last scroll direction

### Changed
- The Lumen apps slider in Explore is slower, with longer transitions, animated page dots and a progress fill on the active dot
- Anti-features in the app detail now show an icon matching each one
- Releases, including beta versions, are published as the latest release

## [0.5.7-beta]

### Fixed
- An update now stays in the Home update list until the install is really confirmed and the installed version is compared with the latest version
- The three-dot menu has four equal sharper corners and its pointer arrow sits exactly under the button

### Added
- Downloads: long-press an installed item with a kept APK to delete only its APK (the item stays in the list) or extract the APK into a chosen folder
- The Explore Lumen apps slider now plays automatically with strongly animated transitions and returns to the first slide after the last one

## [0.5.6-beta]

### Changed
- Explore hero is now a slider that introduces Lumen's own apps (Terbian, Pond and Proxy Share) with their icons and background colors taken from each icon
- The three-dot menu now has equal, larger corners on all four sides and the pointer arrow moved so it no longer breaks the shape
- Pull-to-refresh indicator now floats above the content instead of pushing it down

## [0.5.5]

### Changed
- All pull-to-refresh views now use the Material 3 Expressive loading indicator in a round container, and the content slides down while you pull
- Releases are published as stable and marked as latest instead of pre-release

## [0.5.4] - Beta

### Fixed
- Updates are no longer taken from debug APKs: debug builds are ignored when a release APK exists
- `com.rk.xededitor` is removed from every list, including Home, Explore, search and repository indexes

### Changed
- The profile README on the GitHub account page now sits right under the account header, above the repositories

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
