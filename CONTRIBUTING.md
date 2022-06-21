# Contributing

Ultrasonic development is a community project, and contributions are welcomed.

First, see if your issue haven’t been yet reported [here](https://github.com/ultrasonic/ultrasonic/issues),
then, please, first discuss the change you wish to make via [a new issue](https://github.com/ultrasonic/ultrasonic/issues/new).

## Contributing Translations

Interested in help to translate Ultrasonic? You can contribute in our
[Transifex team](https://www.transifex.com/ultrasonic/ultrasonic/).

## Contributing Code

By default Pull Request should be opened against **develop** branch, PR against **master** branch should be used only
 for critical bugfixes.

### Here are a few guidelines you should follow before submitting:

1. **License Acceptance:** All contributions must be licensed as [GNU GPLv3](LICENSE) to be accepted.
Use `git commit --signoff` to acknowledge this. 
2. **No Breakage:** New features or changes to existing ones must not degrade the user experience.
3. **Coding standards:** best-practices should be followed, comment generously, and avoid "clever" algorithms.
Refactoring existing messes is great, but watch out for breakage.
4. **No large PR:** Try to limit the scope of PR only to the related issue, so it will be easier to review
and test.

### Pull Request Process
On each Pull Request Github runs a number of checks to make sure there are no problems.

#### Signed commits
Commits must be signed. [See here how to set it up](https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-commits)

#### KtLint
This programm checks if the source code is formatted correctly.
You can run it yourself locally with 

`./gradlew -Pqc ktlintFormat`

Running this command will fix common problems and will notify you of problems it couldn't fix automatically.

#### Detekt

Detekt is a static analyser. It helps to find potential bugs in our code.

You can run it yourself locally with 

`./gradlew -Pqc detekt`

There is a "baseline" file, in which errors which have been in the code base before are noted.
Sometimes it is necessary to regenerate this file by running:

`./gradlew -Pqc detektBaseline`

#### Lint
Lint looks for general problems in the code or unused resources etc. 
You can run it with

`./gradlew -Pqc lintRelease`

If there is a need to regenerate the baseline, remove `ultrasonic/lint-baseline.xml` and rerun the command.


