# BM25Similarity/StableTflSimilarity k3 query-term saturation is skipped for duplicate SHOULD clauses when 2 &lt;= minimumNumberShouldMatch &lt; number of SHOULD clauses

Bug LFV-103 · Area: search / BooleanQuery rewrite and similarities · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You turn on BM25's opt-in k3 parameter (new BM25Similarity(1.2f, 0.75f, true, 8f), or StableTflSimilarity with k3), whose javadoc promises that a term repeated in a query is weighted ((k3 + 1) * qtf) / (k3 + qtf) instead of linearly, and you search with a BooleanQuery that repeats a SHOULD term, for example QueryBuilder.createMinShouldMatchQuery('body', 'a b', fraction). You expect a document that matches every clause to get the same saturated score whatever minimumNumberShouldMatch is. Instead, when 2 &lt;= minimumNumberShouldMatch &lt; the number of SHOULD clauses, the repeated term is summed linearly: with k3=8 the document 'a b' scores 1.0150898 at msm=2 but 0.9568421 at msm=1 and at msm=3 (all clauses required).

The test shows that `BM25Similarity` k3 query-term saturation is skipped for a duplicate SHOULD term when 2 &lt;= minimumNumberShouldMatch &lt; number of SHOULD clauses: query SHOULD a, a, b against the one document 'a b', which matches all three clauses.

## Expected vs actual

Expected the msm=2 score to equal the msm=1 score, 0.3661408 (1.8 * s(a) + s(b), the documented ((k3 + 1) * qtf) / (k3 + qtf) weight). Actual: 0.39229372, the linear sum 2 * s(a) + s(b).

## Files

- [src/org/apache/lucene/search/TestK3SaturationMinShouldMatch.java](src/org/apache/lucene/search/TestK3SaturationMinShouldMatch.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestK3SaturationMinShouldMatch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/boolean-rewrite-dispatch/](../../models/boolean-rewrite-dispatch/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
