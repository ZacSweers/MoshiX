Releasing
=========

1. Update the `CHANGELOG.md` for the impending release.
2. Run `./release.sh`. This will:
    - Automatically determine the new version from CHANGELOG.md
    - Update all `gradle.properties` files with the release version
    - Commit and tag the release
    - Run `./publish.sh`
    - Update to the next SNAPSHOT version
    - Commit and push everything

The script accepts an optional version type argument:
- `./release.sh` - defaults to `--patch` (e.g., 0.34.2 -> 0.34.3)
- `./release.sh --minor` - bumps minor version (e.g., 0.34.2 -> 0.35.0)
- `./release.sh --major` - bumps major version (e.g., 0.34.2 -> 1.0.0)
