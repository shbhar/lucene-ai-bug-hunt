# BpVectorReorderer with an executor can return a different document order from run to run for the same vectors: a reorder task's two centroids are per-thread state, and another reorder task that the fork/join pool runs on the same thread during a join overwrites them (branch_11x and main, both unreleased; in no release)

Finding LFV-1591 · Area: misc / index: BpVectorReorderer (BP doc-id reordering over a float vector field: computeDocMap, BPReorderingMergePolicy, the command line); a class that main and branch_11x have, both unreleased, and no release · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You reorder the documents of a segment by a vector field with BpVectorReorderer and hand it an executor (a merge through BPReorderingMergePolicy gets one from the stock merge schedulers; a direct computeDocMap call can pass one), and you expect the executor to change only how fast the order is computed: its javadoc says it 'may be used to parallelize reordering work'. Instead, on branch_11x and main (both unreleased; no release has the class), the order itself can change from run to run for the same vectors: a reorder task keeps the two centroids it works against in arrays that belong to its thread, and while that thread waits for the task's forked sub-tasks the fork/join pool can run another reorder task on it, which overwrites them. Run on branch_11x f4fd551ca6de: the recorded test (40,000 vectors of one dimension; the call with an executor, a pool of three threads, made up to 100 times from one of its workers) failed in 10 of 10 launches with -ea and in 10 of 10 with -da; with 400,000 clustered vectors of 32 dimensions forced merges under SerialMergeScheduler at 16 processors ended with 3 different document orders in 10 runs in one launch and with one order in 6 of 6 in another; every result was a valid permutation, calls on 16,000 vectors gave one order and so did calls without an executor on the vectors of these runs (on other vectors the first reorder call of a JVM can differ from its later calls even without an executor, for a reason that is not this defect), no javadoc promises a repeatable order, and no wrong search result was run or is claimed.

## Expected vs actual

- Expected: You expect the executor to change only how fast the order is computed: its javadoc says it 'may be used to parallelize reordering work'.
- Actual: On branch_11x and main (both unreleased; no release has the class), the order itself can change from run to run for the same vectors: a reorder task keeps the two centroids it works against in arrays that belong to its thread, and while that thread waits for the task's forked sub-tasks the fork/join pool can run another reorder task on it, which overwrites them.

## Files

- [src/org/apache/lucene/misc/index/TestBpVectorReordererExecutorOrder.java](src/org/apache/lucene/misc/index/TestBpVectorReordererExecutorOrder.java) (JUnit test)

## Run it

- Tested on Lucene main 77d56fefb355 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off (the outcome depends on thread timing: see the note).
- Tested on Lucene branch_11x 4e8fd3dc3bc4 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene main 8506598d7a98 (a commit of 2026-11-25): reproduced, with assertions on and off (the outcome depends on thread timing: see the note).
- Tested on Lucene branch_11x 55ef56a69344 (a commit of 2026-11-25): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- In no release: the class BpVectorReorderer is on Lucene main (77d56fefb355) and on branch_11x (4e8fd3dc3bc4), both unreleased when read on 2026-11-26, and the 10.6.0 release does not have it (with the 10.6.0 jars the test does not compile: cannot find symbol, class BpVectorReorderer), so this page gives no recipe for a release. The test depends on thread timing, so a launch can pass on a build that has the defect. What the test does: it computes the order of 40,000 one-dimension vectors without an executor, then makes the same call with an executor (a TaskExecutor over a ForkJoinPool of three threads) up to 100 times from one of the pool's workers, and fails at the first call whose order is not the order without an executor, with java.lang.AssertionError: order with an executor, position N expected:&lt;..&gt; but was:&lt;..&gt; (position 9999 in 56 of the 60 launches of the block below on 2026-11-25). The vectors hold no random numbers, so the test's input does not depend on the seed; whether a call differs depends on thread timing. How often it failed, one JVM a launch, JDK 25.0.3, Linux aarch64: with four processors visible to the JVM no launch on record passed (among them 40 of 40 launches of a copy with the same code on branch_11x 8a72db074350 and main 1640b5c394de on 2026-11-25, 20 of 20 launches of the block below on branch_11x 55ef56a69344 and main 8506598d7a98 that day, and 20 of 20 launches of the block below on branch_11x 4e8fd3dc3bc4 and main 77d56fefb355 on 2026-11-26, 5 with and 5 without assertions on each); with every processor of a 64-processor host visible it failed in 40 of 40 launches on 2026-11-24 (branch_11x f4fd551ca6de and main b1099362566b), in 103 of 105 launches of the copy on 2026-11-25, and in 40 of 40 launches of the block below, started as printed, on branch_11x 55ef56a69344 and main 8506598d7a98 that day (10 with and 10 without assertions on each). The two launches that passed were among 25 on branch_11x without assertions under the parallel collector (-XX:+UseParallelGC) with every processor visible: in a count over 30 such JVMs, 5 to 27 of 400 calls differed in each, and in 2 of the 30 none of the first 100 did. A launch that passes does not show that the defect is absent: start it again. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there. The test is in the package of the class because it calls the package-private computeValueMap, the method that the public computeDocMap calls. For the Gradle alternative at the end of this page the module is lucene/misc: with the test copied to lucene/misc/src/test/org/apache/lucene/misc/index/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 55ef56a69344 and on main 8506598d7a98, with -Ptests.asserts=true and with -Ptests.asserts=false (one run each). A Gradle run can pass too: of 34 Gradle runs of the copy on branch_11x 8a72db074350 on 2026-11-25, 33 reported 1 test completed, 1 failed, and one run without assertions passed (three further runs with its seed failed).
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: order with an executor, position N expected:&lt;..&gt; but was:&lt;..&gt;; the test depends on thread timing, so a run that reports 1 test successful does not show that the defect is absent, and the launch is to be repeated.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBpVectorReordererExecutorOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
