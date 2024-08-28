Releasing
=========

1. Change the version in `gradle.properties` to a non-SNAPSHOT version.
  * Note - do this in both the top-level file and `moshi-ir/moshi-gradle-plugin`
2. Update the `CHANGELOG.md` for the impending release.
3. `git commit -am "Prepare for release X.Y.Z."` (where X.Y.Z is the new version)
4. `git tag -a X.Y.Z -m "Version X.Y.Z"` (where X.Y.Z is the new version)
5. `./publish.sh`
6. Update the `gradle.properties` to the next SNAPSHOT version.
7. `git commit -am "Prepare next development version."`
8. `git push && git push --tags`
