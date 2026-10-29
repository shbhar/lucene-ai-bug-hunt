# SpanNotQuery with a negative post (or dist) drops every include span that follows the last exclude span in a document, because NO_MORE_POSITIONS - post overflows int

Bug LFV-279 · Area: queries / span queries (SpanNotQuery: the exclusion filter in SpanNotWeight#getSpans) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-279.html](../../bugs/LFV-279.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the text 'b a c' and run new SpanNotQuery(a, b, 0, -1), whose javadoc says it keeps the 'a' spans that do not overlap a 'b' and that a negative post only allows some overlap; since 'b' at position 0 overlaps neither 'a', you expect both spans [1,2) and [2,3), exactly as with post = 0. Instead you get only [1,2): once the first 'a' has used up the document's exclude spans, the exclude iterator sits at NO_MORE_POSITIONS (Integer.MAX_VALUE) and SpanNotQuery.java:210 computes Integer.MAX_VALUE - post, which wraps to a negative int for any post &lt; 0, so every later 'a' in the document is rejected. Nested as spanNear([spanNot(a, b, 0, -1), c], 0, true), the document no longer matches at all (count 0 instead of 1).

The test shows that `SpanNotQuery(a, b, 0, -1)` over the text "b a c" drops the second "a" span, because once the exclude spans are used up `Integer.MAX_VALUE - post` overflows (SpanNotQuery.java:210).

## Expected vs actual

Expected spans `[1,2), [2,3)` (as with post = 0, since "b" overlaps neither "a"); actual only `[1,2)`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queries/spans/TestSpanNotNegativePost.java](src/org/apache/lucene/queries/spans/TestSpanNotNegativePost.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.spans.TestSpanNotNegativePost
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
