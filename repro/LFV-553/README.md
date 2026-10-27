# MoreLikeThis with maxQueryTerms = 0 throws NullPointerException from like(...), retrieveInterestingTerms(...) and the rewrite of MoreLikeThisQuery or the XML parser's LikeThisQuery, instead of returning a query with no terms (regression since 4.10.0)

Bug LFV-553 · Area: queries / MoreLikeThis term selection (MoreLikeThis.createQueue bounded priority queue; MoreLikeThisQuery; XML query parser LikeThisQueryBuilder) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set MoreLikeThis.setMaxQueryTerms(0), whose javadoc says only 'the maximum number of query terms that will be included in any generated query', and call like(...) or retrieveInterestingTerms(...) on text with at least one eligible term, or search with a MoreLikeThisQuery or XML &lt;LikeThisQuery&gt; that carries maxQueryTerms 0. You expect a query with no terms (or an up-front IllegalArgumentException). Instead createQueue sizes its priority queue to limit = min(0, #terms) = 0, finds 'queue.size &lt; limit' false for the first eligible term, and dereferences queue.top, which is null for a zero-capacity queue, so the call throws 'NullPointerException: Cannot read field "score" because "term" is null' (MoreLikeThis.java:669).

## Expected vs actual

- Expected: You expect a query with no terms (or an up-front IllegalArgumentException).
- Actual: createQueue sizes its priority queue to limit = min(0, #terms) = 0, finds 'queue.size &lt; limit' false for the first eligible term, and dereferences queue.top, which is null for a zero-capacity queue, so the call throws 'NullPointerException: Cannot read field "score" because "term" is null' (MoreLikeThis.java:669).

## Files

- [src/org/apache/lucene/queries/mlt/TestMoreLikeThisZeroMaxQueryTerms.java](src/org/apache/lucene/queries/mlt/TestMoreLikeThisZeroMaxQueryTerms.java) (JUnit test)
- [src2/org/apache/lucene/queries/mlt/TestMoreLikeThisZeroMaxQueryTerms.java](src2/org/apache/lucene/queries/mlt/TestMoreLikeThisZeroMaxQueryTerms.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisZeroMaxQueryTerms
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisZeroMaxQueryTerms
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/queries-mlt-commonterms-reference/](../../models/queries-mlt-commonterms-reference/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
