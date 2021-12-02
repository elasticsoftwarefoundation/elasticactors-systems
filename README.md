Elastic Actors - Systems
=====================

Current useful implementations (plugins / extensions) of Elastic Actors

### Current released version

[![CI](https://github.com/elasticsoftwarefoundation/elasticactors-systems/actions/workflows/maven.yml/badge.svg)](https://github.com/elasticsoftwarefoundation/elasticactors-systems/actions/workflows/maven.yml)
[![License: Apache 2](https://img.shields.io/badge/LICENSE-Apache2-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt)
[![Maven Central](https://img.shields.io/maven-central/v/org.elasticsoftwarefoundation.elasticactors-systems/elasticactors-systems.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.elasticsoftwarefoundation.elasticactors-systems%22)

### Notes on versioning

This project gets updated when there are breaking changes on Elastic Actors.

Since these only happen in major and minor version changes, it's important to keep them in sync, 
if an equivalent version is available. If a version is not available, using the most recent 
version is generally safe.

### Release process

This project uses the Maven Release Plugin and GitHub Actions to create releases.\
Just run `mvn release:prepare release:perform` to select the version to be released and create a
VCS tag.

GitHub Actions will start [the build process](https://github.com/elasticsoftwarefoundation/elasticactors-systems/actions/workflows/maven-publish.yml).

If successful, the build will be automatically published to [Maven Central](https://repo.maven.apache.org/maven2/org/elasticsoftwarefoundation/elasticactors-systems/).
