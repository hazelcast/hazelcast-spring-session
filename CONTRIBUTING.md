# Contributing to Hazelcast Spring Session

Hazelcast Spring Session is Open Source software, licensed under the [Apache 2.0 license](LICENSE).

There are multiple ways to contribute:

1. [Reporting an issue](#issue-reports)
2. [Sending a pull request](#pull-requests).
   Note that you don't need to be a developer to help us.
   Contributions that improve the documentation are always appreciated.

If you need assistance, you can ask for help in the [Community Slack](https://slack.hazelcast.com/).

## Issue Reports

Thanks for reporting your issue.
To help us resolve your issue quickly and efficiently, we need as much data for diagnostics as possible.
Please share with us the following information:

1. Exact Hazelcast Spring Session, Hazelcast Platform and Spring versions that you use (_e.g._ `5.5.0`, also whether it is a minor release, or the latest snapshot).
2. Cluster size, _i.e._ the number of Hazelcast cluster members.
3. Java version and distribution.
   It is also helpful to mention the JVM parameters.
4. Operating system.
   If it is Linux, kernel version is helpful.
5. Logs and stack traces, if available.
6. Detailed description of the steps to reproduce your issue.
7. Unit test with the reproducer

## Pull requests

Thanks a lot for creating your <abbr title="Pull Request">PR</abbr>!

A PR can target many different subjects:

* [Documentation](https://github.com/hazelcast/hz-docs):
* Fix a bug
* Add a feature
* Add additional tests to improve the test coverage, or fix flaky tests
* Anything else that makes Hazelcast better!

All PRs follow the same process:

1. Contributions are submitted, reviewed, and accepted using the PR system on GitHub.
2. For first time contributors, our bot will automatically ask you to sign the Hazelcast Contributor Agreement on the
   PR.
3. The latest changes are in the `main` branch.
4. Make sure to design clean commits that are easily readable.
   That includes descriptive commit messages.
5. Please keep your PRs as small as possible, _i.e._ if you plan to perform a huge change, do not submit a single and
   large PR for it.
   For an enhancement or larger feature, you can create a GitHub issue first to discuss.
6. Before you push, run:
   ```bash
   ./gradlew build
   ```
   Pushing your PR once it is free of CheckStyle errors.
7. If you submit a PR as the solution to a specific issue, please mention the issue number either in the PR description
   or commit message.
