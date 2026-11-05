# SparseFixedBitSet.clear(int) and clear(int, int) drop a 4096-bit block's long[] when the block becomes empty but never subtract its bytes from ramBytesUsed, and a later set in that block adds a new array's bytes, so set/clear churn makes ramBytesUsed() grow without bound (24 bytes per set(0)/clear(0) cycle) while the real footprint stays constant (every release since 5.0.0)

Finding LFV-1281 · Area: core / util: SparseFixedBitSet (Accountable RAM accounting) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1281.html](../../bugs/LFV-1281.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a SparseFixedBitSet, set one bit and clear it again, so the 4096-bit block that held it becomes empty and the class drops that block's long[]. You expect ramBytesUsed() to return to the value of a fresh set: Accountable documents it as an estimate of the heap the object uses, and the class computes it exactly from its arrays in the constructor and in clear(). Instead the dropped array's 24 bytes stay counted (new SparseFixedBitSet(1) reports 88, and 112 after set(0) and clear(0)), and every later set in that block adds a new array's bytes, so repeating the cycle makes the estimate grow without bound while the set's real footprint stays the same.

## Expected vs actual

- Expected: 88: the block is empty and its array was dropped, so the estimate equals a fresh set's.
- Actual: `AssertionError: expected:<88> but was:<112>`: the dropped array's 24 bytes stay counted.

## Files

- [src/org/apache/lucene/util/TestSparseFixedBitSetRamBytesUsed.java](src/org/apache/lucene/util/TestSparseFixedBitSetRamBytesUsed.java) (JUnit test)

## Run it

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestSparseFixedBitSetRamBytesUsed
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSparseFixedBitSetRamBytesUsed.java](TestSparseFixedBitSetRamBytesUsed.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
