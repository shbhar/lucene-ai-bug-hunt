# MoreLikeThis keeps the lowest-scoring selected terms when the query reaches the boolean clause limit, so the truncated query silently drops the most interesting terms (regression since 4.10.0)

Bug LFV-571 · Area: queries / MoreLikeThis query construction (MoreLikeThis.createQuery truncation at IndexSearcher.getMaxClauseCount) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-571.html](../../bugs/LFV-571.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You let MoreLikeThis select more terms than the global boolean clause limit, for example setMaxQueryTerms(2000) on a long text under the default limit of 1024, and call like(...). You expect the query, which createQuery deliberately truncates to the clause limit instead of failing, to keep the 1024 most interesting (highest tf*idf) of the selected terms, as it did up to 4.9.1. Instead createQuery pops its queue lowest score first and stops at the first TooManyClauses, so the query holds the 1024 lowest-scoring selected terms and none of the best ones (in the repro, 0 of the 76 top words survive).

## Expected vs actual

- Expected: All 76 best words in the 1024-clause query (as with `maxQueryTerms` 1024), and `{a, b, c}` under the limit of 3.
- Actual: 0 of the 76 best words, through `MoreLikeThis` and `MoreLikeThisQuery`, and `{d, e, f}`; the controls pass.

## Files

- [src/org/apache/lucene/queries/mlt/TestMoreLikeThisClauseLimitKeepsBestTerms.java](src/org/apache/lucene/queries/mlt/TestMoreLikeThisClauseLimitKeepsBestTerms.java) (JUnit test)
- [src2/org/apache/lucene/queries/mlt/TestMoreLikeThisClauseLimitKeepsBestTerms.java](src2/org/apache/lucene/queries/mlt/TestMoreLikeThisClauseLimitKeepsBestTerms.java) (JUnit test)
- [src3/org/apache/lucene/queries/mlt/TestMoreLikeThisClauseLimitKeepsBestTerms.java](src3/org/apache/lucene/queries/mlt/TestMoreLikeThisClauseLimitKeepsBestTerms.java) (JUnit test)

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
javac -d classes-src3 -cp "$CP" $(find src3 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisClauseLimitKeepsBestTerms
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisClauseLimitKeepsBestTerms
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src3:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.mlt.TestMoreLikeThisClauseLimitKeepsBestTerms
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestMoreLikeThisClauseLimit.java](TestMoreLikeThisClauseLimit.java)
- [TestMoreLikeThisClauseLimitKeepsBestTerms.java](TestMoreLikeThisClauseLimitKeepsBestTerms.java)
- [TestMltClauseLimitV1.java](TestMltClauseLimitV1.java)
- [TestMltClauseLimitReachability.java](TestMltClauseLimitReachability.java)

Formal models and scripts this bug's part of the hunt used: [../../models/queries-mlt-commonterms-reference/](../../models/queries-mlt-commonterms-reference/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
