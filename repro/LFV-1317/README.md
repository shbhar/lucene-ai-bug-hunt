# FixedBitSet.xor with a longer set that fits in the same number of 64-bit words copies that set's bits at or above length() into the ghost bits, so cardinality, nextSetBit, equals and hashCode report bits the set cannot hold (since 4.7.0)

Finding LFV-1317 · Area: core / util: FixedBitSet (xor) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1317.html](../../bugs/LFV-1317.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You make a 10-bit FixedBitSet a and a 20-bit FixedBitSet b with only bit 15 set, and call a.xor(b). You expect a to stay empty, because none of its bits 0..9 changed and the class keeps the bits past length() (its 'ghost bits') clear, as cardinality, nextSetBit, equals and hashCode rely on. Instead xor XORs whole 64-bit words and checks only that b has no more words than a (both have one), so bit 15 lands in a's word: a.cardinality() returns 1 and a.nextSetBit(0) returns 15 for a set of length 10, and a no longer equals an empty 10-bit set.

## Expected vs actual

- Expected: `a.cardinality()` is 0 (the 10-bit set stays empty) and the test passes.
- Actual: `java.lang.AssertionError: expected:<0> but was:<1>`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/TestFixedBitSetXorLongerSet.java](src/org/apache/lucene/util/TestFixedBitSetXorLongerSet.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.TestFixedBitSetXorLongerSet
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFixedBitSetXorLongerSet.java](TestFixedBitSetXorLongerSet.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
