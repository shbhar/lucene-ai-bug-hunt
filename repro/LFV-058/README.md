# SparseFixedBitSet.nextClearBit returns NO_MORE_DOCS in the last 4096-bit block

Bug LFV-058 · Area: util / bitsets · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call nextClearBit on a SparseFixedBitSet longer than 2^31-4096 bits at a position in its last 4096-bit block. You expect the next clear bit. Instead blockBase + 4096 wraps, it returns NO_MORE_DOCS, and BitSetIterator.docIDRunEnd then claims absent documents (DenseConjunctionBulkScorer collected 3,966 docs instead of 1 in the repro).

The test shows that `SparseFixedBitSet.nextClearBit` returns NO_MORE_DOCS for a position in the last 4096-bit block of a set longer than 2^31-4096 bits, because `blockBase + 4096` overflows to Integer.MIN_VALUE.

## Expected vs actual

Expected: on an empty `SparseFixedBitSet(Integer.MAX_VALUE)`, `nextClearBit(Integer.MAX_VALUE - 1)` = 2147483646. Actual: 2147483647 (NO_MORE_DOCS).

## Files

- [src/org/apache/lucene/util/TestSparseFixedBitSetLastBlock.java](src/org/apache/lucene/util/TestSparseFixedBitSetLastBlock.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.TestSparseFixedBitSetLastBlock
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-overflow-at-scale/](../../models/study-overflow-at-scale/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
