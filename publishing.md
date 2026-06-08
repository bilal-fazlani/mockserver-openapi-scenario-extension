# Publishing

This project uses the Vanniktech Gradle Maven Publish plugin to publish signed releases to Maven Central through the Sonatype Central Portal.

## Coordinates

```text
groupId:    com.bilal-fazlani
artifactId: mockserver-openapi-scenario-extension
version:    VERSION_NAME from gradle.properties, or -PVERSION_NAME for release overrides
```

Before the first release, confirm that the `com.bilal-fazlani` namespace is registered and verified in the Sonatype Central Portal.

## Versioning Model

`main` carries the next development snapshot version in `gradle.properties`:

```properties
VERSION_NAME=0.0.2-SNAPSHOT
```

That value is the version used by normal Gradle commands when no `-PVERSION_NAME=...` override is supplied.

After each release, bump `VERSION_NAME` on `main` to the next snapshot:

```text
release v0.0.1 -> main becomes 0.0.2-SNAPSHOT
release v0.0.2 -> main becomes 0.0.3-SNAPSHOT
release v0.1.0 -> main becomes 0.1.1-SNAPSHOT
```

This keeps the next snapshot version explicit in source control. The build does not infer the next version from Git tags.

## Required GitHub Secrets

Create these repository secrets:

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_IN_MEMORY_KEY
SIGNING_IN_MEMORY_KEY_PASSWORD
```

`MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` should be a Sonatype Central Portal user token, not the interactive login password.

`SIGNING_IN_MEMORY_KEY` should be an ASCII-armored private GPG key:

```bash
gpg --export-secret-keys --armor <key-id>
```

`SIGNING_IN_MEMORY_KEY_PASSWORD` is the passphrase for that key.

## Release Flow

Before creating a release tag, make sure the code on `main` is at the snapshot version for the release you are about to cut. For example, if `main` says:

```properties
VERSION_NAME=0.0.2-SNAPSHOT
```

then the next release tag should normally be:

```bash
git tag v0.0.2
git push origin v0.0.2
```

The GitHub Actions publish workflow strips the leading `v` and runs:

```bash
./gradlew test runtimeJar publishAndReleaseToMavenCentral -PVERSION_NAME=0.0.2
```

The Docker image workflow also runs for the same tag and publishes:

```text
ghcr.io/bilal-fazlani/mockserver-openapi-scenario-extension:0.0.2
ghcr.io/bilal-fazlani/mockserver-openapi-scenario-extension:0.0
ghcr.io/bilal-fazlani/mockserver-openapi-scenario-extension:latest
```

The GHCR workflow uses `GITHUB_TOKEN`, so it does not require additional registry secrets. After the first publish, make sure the package visibility is public if consumers should be able to pull it without authentication.

After the release is published, bump `gradle.properties` on `main`:

```properties
VERSION_NAME=0.0.3-SNAPSHOT
```

Commit and push that bump so the next snapshot line is ready.

The release may take several minutes to appear in Maven Central after the workflow completes.

## Snapshot Flow

Snapshots are useful for testing the latest `main` without cutting a release. Before publishing snapshots, enable `-SNAPSHOT` publishing for the `com.bilal-fazlani` namespace in the Sonatype Central Portal.

With the tracked `gradle.properties` version:

```properties
VERSION_NAME=0.0.2-SNAPSHOT
```

publish a snapshot with:

```bash
./gradlew test runtimeJar publishToMavenCentral
```

Do not use `publishAndReleaseToMavenCentral` for snapshots.

A GitHub Actions workflow for publishing snapshots on every `main` commit would run the same command:

```yaml
name: Publish Snapshot

on:
  push:
    branches:
      - main

permissions:
  contents: read

jobs:
  publish-snapshot:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - name: Publish snapshot
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.SIGNING_IN_MEMORY_KEY }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: ${{ secrets.SIGNING_IN_MEMORY_KEY_PASSWORD }}
        run: ./gradlew test runtimeJar publishToMavenCentral
```

Consumers need the snapshot repository:

```kotlin
repositories {
    maven {
        name = "Central Portal Snapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        content {
            includeModule("com.bilal-fazlani", "mockserver-openapi-scenario-extension")
        }
    }
    mavenCentral()
}
```

## Local Dry Run

You can verify the Maven publication locally without uploading:

```bash
./gradlew publishToMavenLocal
```
