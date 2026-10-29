# LRUQueryCache MinSegmentSizePredicate overflows maxDoc * 2, so segments of 2^30+ docs are never cached

Bug LFV-081 · Area: search / query cache · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use the query cache with its MinSegmentSizePredicate on a segment of 2^30 or more documents. You expect large segments to be cacheable. Instead maxDoc * 2 overflows int, the predicate rejects them from 2^30 docs on, and such a segment is never cached.

The test shows that LRUQueryCache's default `MinSegmentSizePredicate(10000)` computes `maxDoc * 2` in int, so it rejects a segment of 2^30 docs that its javadoc says is cacheable.

## Expected vs actual

Expected: `test(...)` returns true for the only leaf (2^30 docs, average 2^30). Actual (-ea and -da): false, because 2^30 * 2 wraps to -2^31.

## Files

- [src/org/apache/lucene/search/TestMinSegmentSizePredicateOverflow.java](src/org/apache/lucene/search/TestMinSegmentSizePredicateOverflow.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestMinSegmentSizePredicateOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
