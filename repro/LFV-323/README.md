# RamUsageEstimator.sizeOfCollection adds the assumed backing-array bytes in int arithmetic, so a collection of 536,870,908 or more elements (268,435,453 with 8-byte references) gets a size estimate that is a multiple of 4 GiB too low, and negative when the elements add little

Bug LFV-323 · Area: util / RamUsageEstimator · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You ask RamUsageEstimator.sizeOfCollection (or sizeOfObject) for the size of a very large list, for example Arrays.asList(new Object[536870908]). You expect a size in bytes that is at least the 2,147,483,648 bytes of its backing array, which shallowSizeOf(Object) reports correctly. Instead the method adds the array header and the per-element references in int arithmetic, which wraps, so it returns -2,147,483,616; in general the estimate is too low by a multiple of 2^32 bytes from 536,870,908 elements on (268,435,453 with 8-byte references), and it is negative only when the elements' own sizes add little.

The test shows that RamUsageEstimator.sizeOfCollection adds the backing-array bytes in int arithmetic (RamUsageEstimator.java:307), so a collection of 536,870,908 elements (compressed oops) gets a negative size.

## Expected vs actual

Expected: a size in bytes &gt;= 0. Actual (-ea and -da): sizeOfCollection(Collections.nCopies(536870908, null)) = -2147483616.

## Files

- [src/org/apache/lucene/util/TestRamUsageEstimatorLargeCollection.java](src/org/apache/lucene/util/TestRamUsageEstimatorLargeCollection.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.util.TestRamUsageEstimatorLargeCollection
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/numeric-string-version-utils/](../../models/numeric-string-version-utils/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
