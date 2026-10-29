# TieredMergePolicy.setSegmentsPerTier accepts NaN, and the next IndexWriter flush, commit or NRT reopen then never returns: findMerges spins forever inside the synchronized IndexWriter.updatePendingMerges, blocking every other use of the writer (the other four double setters accept NaN too; with forceMergeDeletesPctAllowed = NaN, forceMergeDeletes expunges nothing)

Bug LFV-447 · Area: index / TieredMergePolicy parameter validation (findMerges allowed-segment-count loop, IndexWriter.updatePendingMerges) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an IndexWriter with the default TieredMergePolicy, call setSegmentsPerTier(Double.NaN), add one document and call flush or commit, expecting the setter to throw IllegalArgumentException as it does for every other value outside 'segmentsPerTier must be &gt;= 2.0', or at least a flush that returns. Instead the setter stores NaN (its only check is 'v &lt; 2.0', which is false for NaN) and flush never returns: findMerges computes mergeFactor = (int) NaN = 0, the level size drops to 0, both exit tests of its while (true) loop stay false, and the thread spins forever while holding the IndexWriter lock, so every other thread that needs the writer blocks. The other four double setters (maxMergedSegmentMB, deletesPctAllowed, floorSegmentMB, forceMergeDeletesPctAllowed) accept NaN through the same ordered-only checks; with forceMergeDeletesPctAllowed = NaN, forceMergeDeletes expunges nothing.

The test shows that TieredMergePolicy.setSegmentsPerTier accepts NaN (its only check is `v < 2.0`), and that the next IndexWriter.flush() then never returns: findMerges spins in its `while (true)` loop at TieredMergePolicy.java:413 while holding the IndexWriter lock.

## Expected vs actual

Expected: setSegmentsPerTier(NaN) throws IllegalArgumentException, or flush() of one document returns within the 10 s `@Timeout`. Actual (-ea and -da): `Test timeout exceeded (>= 10000 msec)`, and the leaked test thread is still RUNNABLE in findMerges via flush.

## Files

- [src/org/apache/lucene/index/TestTieredMergePolicySegmentsPerTierNaN.java](src/org/apache/lucene/index/TestTieredMergePolicySegmentsPerTierNaN.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 2 failed.
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
     execute --select-class org.apache.lucene.index.TestTieredMergePolicySegmentsPerTierNaN
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
