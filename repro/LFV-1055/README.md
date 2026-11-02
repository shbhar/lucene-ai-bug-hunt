# With maxConn = 1, a merge that reuses an HNSW graph with a deleted vector never finishes: InitializedHnswGraphBuilder.rebalanceGraph adds one full graph level per pass until OutOfMemoryError

Finding LFV-1055 · Area: core / HNSW merge-time graph reuse (util/hnsw InitializedHnswGraphBuilder.rebalanceGraph, codecs/lucene99 Lucene99HnswVectorsFormat) · Priority: low · Impact: hang · Novelty: new

Finding page: [../../bugs/LFV-1055.html](../../bugs/LFV-1055.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index vectors with an HNSW format configured with maxConn = 1, a value the format accepts (1 to 512), delete or update one document and call forceMerge(1) (or let a natural merge run), and you expect the merge to finish, as it does in about a second with maxConn = 2 or 16. Instead the merge reuses the old graph, repairs it and calls rebalanceGraph, whose level loop stops only when size * (1/M)^level drops to 0: for M = 1 that target stays at the full size on every level and every node is promoted, so each pass adds one more complete graph level, without end. forceMerge never returns and the heap grows until OutOfMemoryError, which closes the IndexWriter (10.5.x has no abort check in the loop; main and 10.6.0 poll it, so rollback() can stop it).

## Expected vs actual

- Expected: `forceMerge(1)` finishes, as with `maxConn = 2` (13 abort checks), and the test passes.
- Actual: `AssertionError: forceMerge(1) still running after 1,000 abort checks`; 989 of the checks are `rebalanceGraph` promotions.

## Files

- [src/org/apache/lucene/codecs/lucene99/TestHnswMaxConnOneMerge.java](src/org/apache/lucene/codecs/lucene99/TestHnswMaxConnOneMerge.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene99.TestHnswMaxConnOneMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestHnswMaxConnOneMerge.java](TestHnswMaxConnOneMerge.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
