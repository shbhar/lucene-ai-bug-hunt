# IndriAndQuery returns documents that match none of its clauses when a clause uses two-phase iteration (PhraseQuery, doc-values range queries), because IndriDisjunctionScorer iterates the clauses' approximations and never calls TwoPhaseIterator.matches

Bug LFV-874 · Area: search / IndriAndQuery (IndriDisjunctionScorer iteration over two-phase clauses) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an IndriAndQuery from SHOULD clauses, for example the phrase "a b" plus the term c, or a doc-values range n in [1, 10] plus the term c, and expect the documents that match at least one clause, as its javadoc ('matches documents matching combinations of subqueries') says. Instead it also returns documents that are only cheap candidates of the phrase or range clause: 'b a' (both terms, no phrase) in the phrase case and the documents with n=50 and n=100 in the range case, and count agrees with the wrong hits (3 instead of 2, and 4 instead of 2, in the 4-document repro). IndriDisjunctionScorer walks the approximations of its two-phase clauses and never runs their exact matches check; a segment where only one clause has a scorer is exact, so the same document can be rejected in one segment and returned in another.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It also returns documents that are only cheap candidates of the phrase or range clause: 'b a' (both terms, no phrase) in the phrase case and the documents with n=50 and n=100 in the range case, and count agrees with the wrong hits (3 instead of 2, and 4 instead of 2, in the 4-document repro).

## Files

- [src/org/apache/lucene/search/TestIndriAndQueryTwoPhaseClauses.java](src/org/apache/lucene/search/TestIndriAndQueryTwoPhaseClauses.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestIndriAndQueryTwoPhaseClauses
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indri-query-family-composite-score-boost-visitor-explain-laws/](../../models/indri-query-family-composite-score-boost-visitor-explain-laws/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
