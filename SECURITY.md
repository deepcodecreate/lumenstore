# Security Policy

## Supported versions

Only the latest release receives security fixes.

| Version | Supported |
|---------|-----------|
| Latest release | Yes |
| Older releases | No |

## Reporting a vulnerability

Please do not open a public issue for security problems.

Use GitHub's private reporting instead: open the **Security** tab of this repository and choose **Report a vulnerability**. Include:

- What the problem is and which version is affected
- Steps to reproduce, or a proof of concept
- The impact you expect

You can expect a first reply within 7 days. Confirmed issues are fixed as quickly as possible and credited in the release notes unless you prefer to stay anonymous.

## Verifying downloads

Every release APK is published with a `SHA256SUMS.txt` file and a signed build provenance attestation.

```bash
sha256sum -c SHA256SUMS.txt
gh attestation verify Lumenstore-vX.Y.Z.apk --repo deepcodecreate/lumenstore
```

Only install APKs downloaded from the [official releases page](https://github.com/deepcodecreate/lumenstore/releases).

## Scope

In scope: the Lumen Store app, its build workflows and its release artifacts.

Out of scope: vulnerabilities in third-party apps listed or installed through Lumen Store, and in upstream services such as F-Droid, IzzyOnDroid, GitHub, GitLab, Codeberg or Google Play.

## Handling of secrets

- A GitHub token is optional and is stored only in the app's private storage on your device
- Release signing keys are never committed to this repository
- Use a token with the minimum scope (`public_repo` or read-only public repositories)
