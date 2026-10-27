# RamUsageEstimator.sizeOfCollection adds the assumed backing-array bytes in int arithmetic, so a collection of 536,870,908 or more elements (268,435,453 with 8-byte references) gets a size estimate that is a multiple of 4 GiB too low, and negative when the elements add little

Bug LFV-323 · Area: util / RamUsageEstimator · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You ask RamUsageEstimator.sizeOfCollection (or sizeOfObject) for the size of a very large list, for example Arrays.asList(new Object[536870908]). You expect a size in bytes that is at least the 2,147,483,648 bytes of its backing array, which shallowSizeOf(Object) reports correctly. Instead the method adds the array header and the per-element references in int arithmetic, which wraps, so it returns -2,147,483,616; in general the estimate is too low by a multiple of 2^32 bytes from 536,870,908 elements on (268,435,453 with 8-byte references), and it is negative only when the elements' own sizes add little.

## Expected vs actual

- Expected: You expect a size in bytes that is at least the 2,147,483,648 bytes of its backing array, which shallowSizeOf(Object) reports correctly.
- Actual: The method adds the array header and the per-element references in int arithmetic, which wraps, so it returns -2,147,483,616; in general the estimate is too low by a multiple of 2^32 bytes from 536,870,908 elements on (268,435,453 with 8-byte references), and it is negative only when the elements' own sizes add little.

## Files

- [src/org/apache/lucene/util/TestRamUsageEstimatorCollectionSizeOverflow.java](src/org/apache/lucene/util/TestRamUsageEstimatorCollectionSizeOverflow.java) (JUnit test)
- [src/repro/RamUsageCollectionOverflowMain.java](src/repro/RamUsageCollectionOverflowMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx4g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestRamUsageEstimatorCollectionSizeOverflow

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" repro.RamUsageCollectionOverflowMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/numeric-string-version-utils/](../../models/numeric-string-version-utils/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
