# Releasing

Releases are published to Maven Central automatically when a GitHub Release is published from `main`. The version format is `major.minor.patch`:
- **Major**: incremented for breaking changes.
- **Minor**: incremented for each regular release.
- **Patch**: incremented for bug-fix-only releases.

### Prerequisites

The following secrets must be configured in the `moia-oss/matsim-aws` repository settings:
- `CENTRAL_TOKEN_USERNAME` and `CENTRAL_TOKEN_PASSWORD` — credentials from the Sonatype Central Portal account (generated under Account → Generate User Token).
- `GPG_PRIVATE_KEY` — ASCII-armored GPG private key used to sign artifacts (`gpg --armor --export-secret-keys <key-id>`).
- `GPG_PASSPHRASE` — passphrase for the GPG key.

### Steps

1. **Bump the POM version** to the release version on `main`:
   ```bash
   cd matsim-aws-setup
   mvn versions:set -DnewVersion=1.1.0 -DgenerateBackupPoms=false
   git add pom.xml
   git commit -m "release: bump version to 1.1.0"
   git push
   ```

2. **Publish a GitHub Release**: go to the repository → Releases → "Draft a new release" → set the tag to `v1.1.0` targeted at `main` → write release notes → click **Publish release**.

   This triggers the release workflow, which validates that the tag matches the POM version, signs the artifacts, and publishes them to Maven Central
