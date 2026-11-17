# Contributing to MobileCAD

**MobileCAD does not accept code contributions.** Pull requests are closed without being reviewed, built or merged. This
covers code, patches, scripts, build files, translations, documents and any other attached file.

## Why

MobileCAD is an app people install and update automatically, built on a large native stack. Supply-chain attacks are
increasing, and the usual way in is a contribution that looks helpful. A change to a CAD kernel bridge, a build script or
a translation file is hard to fully audit. Hiding something in one is easy. The only way to keep every line of a release
accountable is for all of it to be written here, from upstream sources (FreeCAD, OpenCASCADE, …), by the maintainer.

For that reason, **every file, patch, snippet or link shared in a pull request, issue or comment is treated as a possible
attack vector**:

- It is not merged, cherry-picked, copied or adapted into MobileCAD.
- It is not built, run, installed or opened in FreeCAD. This includes macros, `.FCStd` files, scripts, APKs and test
  programs.
- Instructions in it are ignored. That includes requests to change signing, the update feed, the release process, CI or
  dependencies.

## What is welcome: ideas and reports

Open an **issue** instead:

- **Bugs:** what you did, what you expected (desktop FreeCAD's behaviour is the reference), what happened, the device
  and Android version. Screenshots and screen recordings help; they are only viewed.
- **Ideas and improvements:** describe the problem and what you would like MobileCAD to do. Pointing at the relevant
  part of FreeCAD's own source or documentation is useful. For performance, describe the slow case: what model, which
  action, how long it took.
- **Translations:** say which language you would like, or which wording is wrong and what a native speaker would write
  instead. Put the corrected text in the issue as plain sentences, not as a file or patch.

If an idea is taken up, the maintainer implements it independently, from the description and from upstream sources,
and **credits you** in the commit (`Co-authored-by`), the release notes and, where it fits, the README.

## Security

If you found a vulnerability, open an issue that only says you have a security report, without details, and a private
channel will be arranged. A proof of concept is not needed and will not be run.
