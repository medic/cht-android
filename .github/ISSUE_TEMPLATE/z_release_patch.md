---
name: Patch release
about: Schedule a patch release
title: 'Release vX.Y.Z'
labels: 'Type: Internal process'
assignees: ''

---

# Planning - Product Manager

- [ ] Create an GH Milestone and add this issue to it.
- [ ] Add all the issues to be worked on to the Milestone.

# Development - Release Engineer

When development is ready to begin one of the engineers should be nominated as a Release Engineer. They will be responsible for making sure the following tasks are completed though not necessarily completing them.

- [ ] Write an update in the weekly Product Team call agenda summarising development and acceptance testing progress and identifying any blockers. The Release Engineer is to update this every week until the version is released.

# Releasing - Release Engineer

Once all issues have passed acceptance testing and have been merged into `master` and backported to the release branch release testing can begin.

- [ ] Build an alpha named `v<major>.<minor>.<patch>-alpha.1` as described in the [release docs](https://docs.communityhealthtoolkit.org/core/guides/android/releasing/#alpha-for-release-testing).
  - [ ] Until release testing passes, make sure regressions are fixed in `master`, cherry-pick them into the release branch, and release another alpha.
- [ ] Create a `release_notes_v<major>.<minor>.<patch>` branch from `master` and add a new section in the [CHANGELOG](https://github.com/medic/cht-android/blob/master/CHANGELOG.md).
  - [ ] Ensure all issues are in the GH Milestone, that they're correctly labelled (in particular: they have the right Type, "UI/UX" if they change the UI, and "Breaking change" if appropriate), and have human readable descriptions.
  - [ ] Document any known migration steps and known issues.
  - [ ] Provide description, screenshots, videos, and anything else to help communicate particularly important changes.
  - [ ] Document any required or recommended upgrades to our other products (eg: cht-core, cht-conf, cht-gateway).
  - [ ] Assign the PR to the Director of Technology to review and confirm the documentation on upgrade instructions and breaking changes is sufficient.
- [ ] Create a release in GitHub as described in the [release docs](https://docs.communityhealthtoolkit.org/core/guides/android/releasing/#production-release).

# Publishing - Release Engineer

- [ ] Download the `.apk` files (or `.aab` files) from the assets on the release in GitHub for each reference flavor to publish:
  - `medicmobilegamma`
  - `unbranded`
- [ ] Publish a release for each flavor as described in the [publishing docs](https://docs.communityhealthtoolkit.org/core/guides/android/publishing/#google-play-store).
- [ ] Announce the release on the [CHT forum](https://forum.communityhealthtoolkit.org/c/product/releases/26), under the "Product - Releases" category using this template:
```
*Announcing the release of [{{version}}](https://github.com/medic/cht-android/releases/tag/{{version}}) of cht-android*

This release fixes {{number of bugs}}. Read the release notes for full details: {{url}}
```
- [ ] Mark this issue "done" and close the Milestone.
