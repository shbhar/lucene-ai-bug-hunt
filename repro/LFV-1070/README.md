# BpVectorReorderer never calls the Executor it is given; any non-null executor makes it fork its work onto ForkJoinPool.commonPool()

Finding LFV-1070 · Area: misc / index: BpVectorReorderer (BP doc-id reordering over a vector field, through BPReorderingMergePolicy, computeDocMap and the command line) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1070.html](../../bugs/LFV-1070.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You reorder a segment with BpVectorReorderer and pass an Executor, for example the same-thread intra-merge executor that IndexWriter hands to BPReorderingMergePolicy under SerialMergeScheduler, or the pool the command line builds for --thread-count, and expect the work to run on the calling thread or on that executor, as it does for the sibling BPIndexReorderer. Instead the executor is never called: the tasks are RecursiveActions forked with ForkJoinTask.invokeAll from a thread outside any ForkJoinPool, so any non-null executor only switches forking on and the forked work runs on ForkJoinPool.commonPool(). With a run-on-caller executor and 4,096 two-dimensional vectors, vectors are read on a common-pool worker as well as on the caller; the resulting order is still correct.

## Expected vs actual

- Expected: Only the test thread reads vectors: a same-thread executor keeps all the work on the caller.
- Actual: `AssertionError`: the readers are the test thread and `ForkJoinPool.commonPool-worker-1`.

## Files

- [src/org/apache/lucene/misc/index/TestBpVectorReordererExecutor.java](src/org/apache/lucene/misc/index/TestBpVectorReordererExecutor.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBpVectorReordererExecutor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestBpVectorReordererExecutor.java](TestBpVectorReordererExecutor.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
