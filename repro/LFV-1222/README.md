# A top-k search of a DisjunctionMaxQuery or LogOddsFusionQuery throws a plain RuntimeException instead of the declared IOException when a clause's Scorer.getMaxScore throws IOException, because DisjunctionScoreBlockBoundaryPropagator's sort comparator wraps it (since 8.3.0)

Finding LFV-1222 · Area: core / search: disjunction scoring (DisjunctionScoreBlockBoundaryPropagator, built by DisjunctionMaxScorer and LogOddsFusionScorer for top-k searches) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1222.html](../../bugs/LFV-1222.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a top-k search, IndexSearcher.search(q, 10), of a DisjunctionMaxQuery with tie-breaker 0.1 whose second clause is your own query whose Scorer.getMaxScore throws IOException, and expect that IOException: Scorer.getMaxScore and IndexSearcher.search both declare it, and the same failure on a later, identical getMaxScore call does surface as IOException. Instead the search throws java.lang.RuntimeException wrapping it, from DisjunctionScoreBlockBoundaryPropagator.java:40, because the propagator sorts the clauses with a Comparator whose key extractor calls getMaxScore(NO_MORE_DOCS) and turns the IOException into a RuntimeException (a lambda cannot throw a checked exception). LogOddsFusionQuery builds the same propagator and behaves the same; a search that does not prune (COMPLETE score mode) is unaffected.

## Expected vs actual

- Expected: `search(q, 10)` throws the clause's `java.io.IOException: getMaxScore failed`.
- Actual: It throws `java.lang.RuntimeException: java.io.IOException: getMaxScore failed`, from `DisjunctionScoreBlockBoundaryPropagator.java:40`.

## Files

- [src/org/apache/lucene/search/TestDisjunctionMaxQueryMaxScoreIOException.java](src/org/apache/lucene/search/TestDisjunctionMaxQueryMaxScoreIOException.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces (the repro adapted to an API change on this commit).
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 535A88A57F6CEBAD
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=535A88A57F6CEBAD \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestDisjunctionMaxQueryMaxScoreIOException
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDisjunctionMaxQueryMaxScoreIOException.java](TestDisjunctionMaxQueryMaxScoreIOException.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
