# SweetSpotSimilarity.lengthNorm adds abs(x-min) + abs(x-max) in int, so a field of 1,073,741,848 or more terms (reachable with one custom term frequency) gets a NaN norm: the document is never collected, and default top-k search also skips competitive documents in the same postings block and skip range

Bug LFV-754 · Area: misc / similarities (SweetSpotSimilarity lengthNorm; NaN effect on impacts-based top-k pruning in core search) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score with SweetSpotSimilarity (default factors) and index one document whose DOCS_AND_FREQS field has a length of at least 1,073,741,848, for example one token with custom term frequency 1.1e9 or about 1.07e9 ordinary tokens, expecting the documented lengthNorm 1/sqrt(x) (about 3.05e-5, the ClassicSimilarity value) and an ordinary, low-ranked hit. Instead lengthNorm's int sum |x-1| + |x-1| wraps negative, so the norm and the score are NaN and the document is never collected (exhaustive collection returns 5,999 of 6,000 matches); because the NaN also makes the postings block's maximum score NaN, default top-k search skips that block and the skip range above it, so search(q, 7) on a 10,000-document index misses three documents that score 1.0 and returns documents that score 0.5 instead. ClassicSimilarity on the same index is correct.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: lengthNorm's int sum |x-1| + |x-1| wraps negative, so the norm and the score are NaN and the document is never collected (exhaustive collection returns 5,999 of 6,000 matches); because the NaN also makes the postings block's maximum score NaN, default top-k search skips that block and the skip range above it, so search(q, 7) on a 10,000-document index misses three documents that score 1.0 and returns documents that score 0.5 instead.

## Files

- [src/org/apache/lucene/misc/TestSweetSpotLengthNormOverflow.java](src/org/apache/lucene/misc/TestSweetSpotLengthNormOverflow.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.TestSweetSpotLengthNormOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
