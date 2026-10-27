# A DirectoryReader opened with open(commit, minSupportedMajorVersion, leafSorter) forgets that minimum: isCurrent, openIfChanged and SearcherManager.maybeRefresh throw IndexFormatTooOldException even on an unchanged index

Bug LFV-131 · Area: index / DirectoryReader reopen and isCurrent (expert old-index open) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an index whose segments come from an older Lucene major, which the default open refuses, through the expert DirectoryReader.open(commit, minSupportedMajorVersion, leafSorter) with a lower minimum, and it opens fine. With nothing committed since, you expect isCurrent to return true and openIfChanged(reader) to return null, so that SearcherManager.maybeRefresh does nothing. Instead all three throw IndexFormatTooOldException ('Index has segments derived from Lucene version 8.x and is not supported...'), because the reader does not keep the minimum it was opened with and re-reads the commit with the default Version.MIN_SUPPORTED_MAJOR.

## Expected vs actual

- Expected: You expect isCurrent to return true and openIfChanged(reader) to return null, so that SearcherManager.maybeRefresh does nothing.
- Actual: All three throw IndexFormatTooOldException ('Index has segments derived from Lucene version 8.x and is not supported...'), because the reader does not keep the minimum it was opened with and re-reads the commit with the default Version.MIN_SUPPORTED_MAJOR.

## Files

- [src/org/apache/lucene/index/TestReopenBelowMinSupportedMajor.java](src/org/apache/lucene/index/TestReopenBelowMinSupportedMajor.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.index.TestReopenBelowMinSupportedMajor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
