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
2. **App is migrating to [Kotlin](https://kotlinlang.org/) programming language:** new Pull Requests
should be written in this programming language.
3. **No Breakage:** New features or changes to existing ones must not degrade the user experience.
4. **Coding standards:** best-practices should be followed, comment generously, and avoid "clever" algorithms.
Refactoring existing messes is great, but watch out for breakage.
5. **No large PR:** Try to limit the scope of PR only to the related issue, so it will be easier to review
and test.

### Pull Request Process

1. Ensure all commits are signed-off.
2. Check tests for the new code are added.
3. Check code style is passing.
4. Check code static analysis is passing.
