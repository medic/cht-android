---
name: Major/minor release
about: Schedule a major or minor release
title: 'Release x.y.z'
labels: 'Type: Internal process'
assignees: ''

---

# Planning - Product Manager

- [ ] Create a GH Milestone for the release. We use [semver](http://semver.org) so if there are breaking changes increment the major, otherwise if there are new features increment the minor, otherwise increment the service pack. Breaking changes in our case relate to updated software requirements (egs: minimum Android versions), broken backwards compatibility in an api, or a major visual update that requires user retraining.
- [ ] Add all the issues to be worked on to the Milestone. Ideally each minor release will have one or two features, a handful of improvements, and plenty of bug fixes.
- [ ] Identify any features and improvements in the release that need end-user documentation (beyond eng team documentation improvements) and create corresponding issues in the cht-docs repo
- [ ] Assign an engineer as Release Engineer for this release.

# Development - Release Engineer

When development is ready to begin one of the engineers should be nominated as a Release Engineer. They will be responsible for making sure the following tasks are completed though not necessarily completing them.

- [ ] Raise a new issue called `Update dependencies for <version>`. This should be done early in the release cycle so find a volunteer to take this on and assign it to them.
- [ ] Write an update in the weekly Product Team call agenda summarising development and acceptance testing progress and identifying any blockers. The release Engineer is to update this every week until the version is released.

# Releasing - Release Engineer

Once all issues have passed acceptance testing and have been merged into `master` release testing can begin.

- [ ] Create a new release branch from `master` named `<major>.<minor>.x` in `cht-android`.
- [ ] Build an alpha named `v<major>.<minor>.<patch>-alpha.1` by pushing a git tag and when CI completes successfully notify the QA team that it's ready for release testing.
- [ ] Create a `release_notes_<major>.<minor>.<patch>` branch from `master` and add a new section in the [CHANGELOG](https://github.com/medic/cht-android/blob/master/CHANGELOG.md). Ensure all issues are in the GH Milestone, that they're correctly labelled (in particular: they have the right Type, "UI/UX" if they change the UI, and "Breaking change" if appropriate), and have human readable descriptions. Manually document any known migration steps and known issues. Provide description, screenshots, videos, and anything else to help communicate particularly important changes. Document any required or recommended upgrades to our other products (eg: cht-core, cht-conf, cht-gateway). Assign the PR to a) the Director of Technology, and b) an SRE to review and confirm the documentation on upgrade instructions and breaking changes is sufficient.
- [ ] Until release testing passes, make sure regressions are fixed in `master`, cherry-pick them into the release branch, and release another alpha.
- [ ] Create a release in GitHub from the release branch so it shows up under the [Releases tab](https://github.com/medic/cht-android/releases) with the naming convention `v<major>.<minor>.<patch>`. This will create the git tag automatically. Repeat the release notes in the description of the release.
- [ ] Confirm the release build completes successfully and the new artifacts are available as assets on the release in GitHub.
  
# Publishing - Release Engineer

- [ ] Download the 3 `.apk` files (or 2 `.aab` files) from the assets on the release in GitHub for each reference flavor to publish:
  - `medicmobilegamma`
  - `unbranded`
- [ ] In the [Google Play Console](https://play.google.com/console), for each flavor, create a new `Production` release.
  - Upload app bundles for the flavor
  - Use the new cht-android version as the Release name
  - Add a one sentence summary of the CHANGELOG entry as the Release notes.
- [ ] From the `Releases overview` page in the Google Play Console, confirm that the Release status for the new release is "Available on Google Play" (this could take hours or days).
- [ ] Announce the release on the [CHT forum](https://forum.communityhealthtoolkit.org/c/product/releases/26), under the "Product - Releases" category using this template:
```
*We're excited to announce the release of [{{version}}](https://github.com/medic/cht-android/releases/tag/{{version}}) of cht-android*

New features include {{key_features}}. We've also implemented loads of other improvements and fixed a heap of bugs.

Read the release notes for full details: {{url}}
```
- [ ] Mark this issue "done" and close the Milestone.
