# Contributing to Braive

Short version: **nobody commits directly to `main`.** Work on a branch, open a Pull Request, get one teammate to look at it, then merge.

## Day-to-day workflow

```bash
git checkout main
git pull                              # always start from the latest
git checkout -b feature/short-name    # e.g. feature/battery-percentage

# ...make changes, then:
git add -A
git commit -m "Add battery percentage to Home screen"
git push -u origin feature/short-name
```

Then open a **Pull Request** on GitHub, ask a teammate to review, and merge when approved.
After merging: `git checkout main && git pull` and delete the old branch.

### Branch names
`feature/…` new functionality · `fix/…` bug fixes · `docs/…` documentation

### Commit messages
Write what the change does, in the present tense: *"Fix tap answer finishing too early"*, not *"fixed stuff"*.

## Rules that protect the project

1. **Never commit** `local.properties`, keystores (`*.jks`), APKs, or any password/API key. `.gitignore` covers these — don't override it.
2. **The BLE contract is shared by two codebases.** If you change a UUID, payload format or timing in the app, change `firmware/` and `docs/BLE_GATT_CONTRACT.md` in the same PR (and vice-versa).
3. **Test with the real glove** for anything touching BLE, taps or vibration. The emulator can't.
4. **Keep PRs small** — one topic per PR is far easier to review.
5. **Pull before you push**, and resolve conflicts locally rather than force-pushing.

## Before opening a PR
- [ ] The app builds (`./gradlew assembleDebug`)
- [ ] You ran it on a phone (and on the glove if relevant)
- [ ] No unrelated files (build output, IDE settings) are included
