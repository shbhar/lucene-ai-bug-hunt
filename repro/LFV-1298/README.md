# FixedBitSet.ensureCapacityAndClear returns the old set bits instead of a cleared set when it grows a set whose backing array is larger than needed: the array-reuse branch wraps the old array without clearing it (since 10.4.0)

Finding LFV-1298 · Area: core / util: FixedBitSet (ensureCapacityAndClear growth) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1298.html](../../bugs/LFV-1298.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a two-word long[] in a 64-bit FixedBitSet (the FixedBitSet(long[], int) constructor says the array 'may be larger' as long as the extra bits are clear), set bit 0, and call FixedBitSet.ensureCapacityAndClear(bits, 64) to make room for bit 64. You expect an empty set, because the javadoc says the method will 'Clear the given bits and ensure it can store a value at desiredBit index', and the two-step code it replaced (ensureCapacity, then clear()) returns one. Instead the method sees that the existing array is already big enough, wraps that same array in a new 128-bit set without zeroing it, and returns a set in which bit 0 is still set (cardinality 1 instead of 0).

## Expected vs actual

- Expected: `ensureCapacityAndClear` returns an empty set (cardinality 0) and the test passes.
- Actual: `java.lang.AssertionError: expected:<0> but was:<1>`: the returned 128-bit set still has bit 0 set.

## Files

- [src/org/apache/lucene/util/TestFixedBitSetEnsureCapacityAndClear.java](src/org/apache/lucene/util/TestFixedBitSetEnsureCapacityAndClear.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.TestFixedBitSetEnsureCapacityAndClear
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFixedBitSetEnsureCapacityAndClear.java](TestFixedBitSetEnsureCapacityAndClear.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
