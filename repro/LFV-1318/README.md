# LongBitSet.or and LongBitSet.xor with a longer set that fits in the same number of 64-bit words copy that set's bits at or above length() into the ghost bits, so cardinality, nextSetBit, intersects, equals and hashCode report bits the set cannot hold (since 4.7.0)

Finding LFV-1318 · Area: core / util: LongBitSet (or, xor) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1318.html](../../bugs/LFV-1318.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You make a 10-bit LongBitSet and a 64-bit LongBitSet other with only bit 20 set, and call or(other) (or xor(other)) on the 10-bit set. You expect the 10-bit set to stay empty, because other has no bit in [0, 10) and the class keeps the bits past length() (its 'ghost bits') clear, which cardinality, nextSetBit, intersects, equals and hashCode rely on. Instead or/xor combine whole 64-bit words and check only that other has no more words (both have one, and the check is an assert), so bit 20 lands in the set's word: cardinality() returns 1 and nextSetBit(0) returns 20 for a set of length 10, with no error.

## Expected vs actual

- Expected: Both 10-bit sets stay empty, `nextSetBit(0)` is -1 for each, and the test passes.
- Actual: `AssertionError: expected:<[-1, -1]> but was:<[20, 20]>`: both sets report bit 20, past their length of 10, with assertions on and off.

## Files

- [src/org/apache/lucene/util/TestLongBitSetOrXorLongerSet.java](src/org/apache/lucene/util/TestLongBitSetOrXorLongerSet.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestLongBitSetOrXorLongerSet
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLongBitSetOrXorLongerSet.java](TestLongBitSetOrXorLongerSet.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
