# Cross-field BlendedTermQuery gives a sparse field's term a docFreq above that field's docCount, so matches score negative (and since 10.3 are dropped from top-k), and a field with no indexed terms makes search throw NullPointerException

Bug LFV-484 · Area: search / BlendedTermQuery cross-field statistics blending · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a BlendedTermQuery for "lucene" on the fields title and keywords, as its javadoc suggests for cross-field search, over an index where two titles contain "lucene" and only one document has a keywords field at all. You expect every matching document back with a non-negative score, and a field that no document has yet to simply match nothing. Instead the query gives keywords:lucene the largest docFreq of all its terms (2) although only 1 document has the keywords field, so BM25's idf turns negative and that document scores -0.10 and is ranked last, since 10.3 it is missing from search(q, 10) when it sits in a segment of its own (count(q) = 3, 2 hits returned), and when no document has the keywords field yet, search throws NullPointerException.

## Expected vs actual

- Expected: You expect every matching document back with a non-negative score, and a field that no document has yet to simply match nothing.
- Actual: The query gives keywords:lucene the largest docFreq of all its terms (2) although only 1 document has the keywords field, so BM25's idf turns negative and that document scores -0.10 and is ranked last, since 10.3 it is missing from search(q, 10) when it sits in a segment of its own (count(q) = 3, 2 hits returned), and when no document has the keywords field yet, search throws NullPointerException.

## Files

- [src/org/apache/lucene/search/TestBlendedTermQueryCrossFieldStats.java](src/org/apache/lucene/search/TestBlendedTermQueryCrossFieldStats.java) (JUnit test)

## Run it

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
     execute --select-class org.apache.lucene.search.TestBlendedTermQueryCrossFieldStats
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
