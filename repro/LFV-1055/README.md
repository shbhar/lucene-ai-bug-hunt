# With maxConn = 1, a merge that reuses an HNSW graph with a deleted vector never finishes: InitializedHnswGraphBuilder.rebalanceGraph adds one full graph level per pass until OutOfMemoryError

Finding LFV-1055 · Area: core / HNSW merge-time graph reuse (util/hnsw InitializedHnswGraphBuilder.rebalanceGraph, codecs/lucene99 Lucene99HnswVectorsFormat) · Priority: low · Impact: hang · Novelty: new

Finding page: [../../bugs/LFV-1055.html](../../bugs/LFV-1055.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by qidaye (open PR #17054) on 2026-11-20 (https://github.com/apache/lucene/pull/17054). Open PR #17054 (qidaye, opened 2026-11-20: 'Fix HNSW rebalanceGraph never terminating when maxConn is 1'; base main; no milestone, no review and no comment as of 2026-11-20) changes InitializedHnswGraphBuilder.java and HnswGraphTestCase.java: for maxConn = 1, rebalanceGraph takes 1/e as the ratio between one graph level and the next. On a build of the pull request as of 2026-11-20 our repro passes, with and without assertions; it fails on main f2b061406c4b, on branch_11x 75eea467ac52 and on the 10.6.0 release. With the pull request's change applied to branch_11x 75eea467ac52 our repro passes there too. The pull request links no issue and does not cite this list. It is not an earlier report of it: this row has been in the site's repository since its commit of 2026-10-30, and the pull request was opened on 2026-11-20. This site files nothing upstream.

## What it shows

You index vectors with an HNSW format configured with maxConn = 1, a value the format accepts (1 to 512), delete or update one document and call forceMerge(1) (or let a natural merge run), and you expect the merge to finish, as it does in about a second with maxConn = 2 or 16. Instead the merge reuses the old graph, repairs it and calls rebalanceGraph, whose level loop stops only when size * (1/M)^level drops to 0: for M = 1 that target stays at the full size on every level and every node is promoted, so each pass adds one more complete graph level, without end. forceMerge never returns and the heap grows until OutOfMemoryError, which closes the IndexWriter (10.5.x has no abort check in the loop; main and 10.6.0 poll it, so rollback() can stop it).

## Expected vs actual

- Expected: `forceMerge(1)` finishes, as with `maxConn = 2` (13 abort checks), and the test passes.
- Actual: `AssertionError: forceMerge(1) still running after 1,000 abort checks`; 989 of the checks are `rebalanceGraph` promotions.

## Files

- [src/org/apache/lucene/codecs/lucene99/TestHnswMaxConnOneMerge.java](src/org/apache/lucene/codecs/lucene99/TestHnswMaxConnOneMerge.java) (JUnit test)

## Run it

- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): still reproduces.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestHnswMaxConnOneMerge.java](TestHnswMaxConnOneMerge.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
