# Doc-values range queries with a skip index return wrong hits when a segment's values span more than Long.MAX_VALUE

Bug LFV-044 · Area: codecs / doc values · Priority: high · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index numeric doc values with a skip index (NumericDocValuesField.indexedField) where one segment's values span more than Long.MAX_VALUE, and run a doc-values range query. You expect the same hits as 10.5.2. Instead the encoded-domain bound transform wraps: 100k random latitudes queried for [45, 90] gave 0 hits instead of 25,157, 64-bit hashes gave 2 instead of 49,931, and some cases add wrong hits.

## Expected vs actual

- Expected: You expect the same hits as 10.5.2.
- Actual: The encoded-domain bound transform wraps: 100k random latitudes queried for [45, 90] gave 0 hits instead of 25,157, 64-bit hashes gave 2 instead of 49,931, and some cases add wrong hits.

## Files

- [src/TestVerifyEncodedRange.java](src/TestVerifyEncodedRange.java) (JUnit test)
- [src/TestVerifyDefaultSearch.java](src/TestVerifyDefaultSearch.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestVerifyEncodedRange \
       --select-class TestVerifyDefaultSearch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-simd-equivalence/](../../models/study-simd-equivalence/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
