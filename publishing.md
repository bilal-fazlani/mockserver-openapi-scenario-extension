# Publishing

This project uses the Vanniktech Gradle Maven Publish plugin to publish signed releases to Maven Central through the Sonatype Central Portal.

## Coordinates

```text
groupId:    com.bilal-fazlani
artifactId: mockserver-openapi-scenario-extension
version:    supplied by -PVERSION_NAME
```

Before the first release, confirm that the `com.bilal-fazlani` namespace is registered and verified in the Sonatype Central Portal.

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

Create and push a version tag:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The GitHub Actions publish workflow strips the leading `v` and runs:

```bash
./gradlew test runtimeJar publishAndReleaseToMavenCentral -PVERSION_NAME=0.1.0
```

The release may take several minutes to appear in Maven Central after the workflow completes.

## Local Dry Run

You can verify the Maven publication locally without uploading:

```bash
./gradlew publishToMavenLocal -PVERSION_NAME=0.1.0-SNAPSHOT
```
