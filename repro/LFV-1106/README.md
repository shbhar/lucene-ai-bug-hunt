# A scoring JoinUtil term join re-sorts its shared BytesRefHash on every non-scoring createWeight, so one join query used by a scoring search and a count() at the same time silently returns too few hits (known: #13018 / open PR #13019, partial overlap) (released since 10.5.0)

Finding LFV-1106 · Area: join / JoinUtil term join (TermsIncludingScoreQuery, TermsQuery) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build one join query with JoinUtil.createJoinQuery(..., ScoreMode.Max) and, as the IndexSearcher thread-safety and Query-reuse javadoc allow, run searcher.search(join, n) in one thread while another thread runs searcher.count(join); every scoring search should return all 20,000 joined documents. Instead each count() builds a TermsQuery whose constructor sorts the join's BytesRefHash again in place, rewriting the ids array that the scoring search iterates as its ords, so the scoring search silently returns fewer documents (18,120 to 19,023 of 20,000 in the recorded runs).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Each count() builds a TermsQuery whose constructor sorts the join's BytesRefHash again in place, rewriting the ids array that the scoring search iterates as its ords, so the scoring search silently returns fewer documents (18,120 to 19,023 of 20,000 in the recorded runs).

## Files

- [src/org/apache/lucene/search/join/TestScoringTermJoinSharedAcrossThreads.java](src/org/apache/lucene/search/join/TestScoringTermJoinSharedAcrossThreads.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestScoringTermJoinSharedAcrossThreads
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
