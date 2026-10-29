# Main (the future 11.0) turns incremental doc-values updates on by default (IndexWriterConfig.DEFAULT_MAX_DOC_VALUES_OVERLAYS = 8; opt-in, 0, in 10.6), but neither the 11.0.0 section of CHANGES.txt nor MIGRATE.md records the change: the only note is the 10.6.0 'Opt-in' entry, because main's 'Enabled by default' note was moved to 10.6.0 and then reworded (documentation defect, unreleased)

Bug LFV-959 · Area: release notes (lucene/CHANGES.txt 11.0.0, lucene/MIGRATE.md 10.x -> 11.0) x index / doc-values updates (IndexWriterConfig#DEFAULT_MAX_DOC_VALUES_OVERLAYS, GITHUB#16418, GITHUB#16648) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You upgrade from Lucene 10.6 to 11.0 and use doc-values updates (for example IndexWriter.updateNumericDocValue) with a default IndexWriterConfig; you expect the 11.0 release notes or the migration guide to say so if the default write path changed, as they do for the query cache, which 11.0 turns off by default. Instead main turns incremental doc-values updates on by default (DEFAULT_MAX_DOC_VALUES_OVERLAYS = 8, against 0 in 10.6), so a one-document update writes a small sparse overlay (402 bytes of update files in the repro) instead of rewriting the column (2892 bytes), yet the only CHANGES.txt entry for the feature is the 10.6.0 one, which says 'Opt-in; enable with IndexWriterConfig#setMaxDocValuesOverlays(n)', and MIGRATE.md says nothing. Main did carry an 'Enabled by default' note until it was moved into the 10.6.0 section and then, correctly for 10.6.0, reworded to the release candidate's text.

## Expected vs actual

- Expected: You expect the 11.0 release notes or the migration guide to say so if the default write path changed, as they do for the query cache, which 11.0 turns off by default.
- Actual: Main turns incremental doc-values updates on by default (DEFAULT_MAX_DOC_VALUES_OVERLAYS = 8, against 0 in 10.6), so a one-document update writes a small sparse overlay (402 bytes of update files in the repro) instead of rewriting the column (2892 bytes), yet the only CHANGES.txt entry for the feature is the 10.6.0 one, which says 'Opt-in; enable with IndexWriterConfig#setMaxDocValuesOverlays(n)', and MIGRATE.md says nothing.

## Files

- [src/org/apache/lucene/index/TestDocValuesOverlaysDefaultAsReleaseNotes.java](src/org/apache/lucene/index/TestDocValuesOverlaysDefaultAsReleaseNotes.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestDocValuesOverlaysDefaultAsReleaseNotes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
