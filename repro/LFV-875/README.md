# IndriAndWeight.explain does not check that its scorer landed on the requested document, so a document that matches no clause is explained as a match with another document's score, or explain throws (EOFException or IndexOutOfBoundsException from a norms read past the clause scorer's last match; AssertionError with -ea)

Bug LFV-875 · Area: search / IndriAndQuery (IndriAndWeight.explain) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with an IndriAndQuery of SHOULD clauses, for example IndriAndQuery(SHOULD f:a), and call IndexSearcher.explain for a document that contains none of the clause terms and is not a hit. You expect a no-match explanation (isMatch false), as TermQuery and a BooleanQuery over the same clauses give. Instead IndriAndWeight.explain advances its scorer to the document, checks the result only with 'assert advanced == doc', and returns Explanation.match(scorer.score,...) for wherever the scorer landed: a match carrying the next hit's score, or, past the last match, an EOFException or IndexOutOfBoundsException from a norms read (AssertionError with -ea).

The test shows that IndriAndWeight.explain does not check where its scorer landed, so a document that matches no clause of `IndriAndQuery(SHOULD f:a)` is explained as a match with the next hit's score.

## Expected vs actual

Expected: `explain(query, 0).isMatch()` is false (doc 0 has no `f:a`). Actual with `-da`: `expected no match for doc 0, got 0.13076457 = sum of:` (doc 1's score); with `-ea`: AssertionError from `assert advanced == doc` at IndriAndWeight.java:99.

## Files

- [src/org/apache/lucene/search/TestIndriAndExplainNonMatch.java](src/org/apache/lucene/search/TestIndriAndExplainNonMatch.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestIndriAndExplainNonMatch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indri-query-family-composite-score-boost-visitor-explain-laws/](../../models/indri-query-family-composite-score-boost-visitor-explain-laws/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
