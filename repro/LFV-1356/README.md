# Bits.applyMask throws ArrayIndexOutOfBoundsException (AssertionError with -ea) on a zero-length destination FixedBitSet instead of doing nothing: the DenseLiveDocs and SparseLiveDocs overrides (a reader's live docs) and FixedBitSet.applyMask do so at an offset past the end, the default implementation at every offset, because each calls nextSetBit(0) on the empty set (latent: no Lucene code passes an empty destination; since 10.2.0)

Finding LFV-1356 · Area: util / Bits.applyMask (DenseLiveDocs, SparseLiveDocs and FixedBitSet overrides, default implementation) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1356.html](../../bugs/LFV-1356.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call applyMask on a segment's live docs (LeafReader.getLiveDocs() of a segment with deletions) with an empty window past the end of the segment, for example liveDocs.applyMask(new FixedBitSet(0), maxDoc + 1). You expect nothing to happen: the Bits.applyMask contract only restricts set bits and an empty set has none, the overrides' own comments say callers may pass an offset beyond maxDoc, and the same call returns normally at offset maxDoc or with a 1-bit all-clear destination at maxDoc + 1. Instead it throws ArrayIndexOutOfBoundsException 'Index 0 out of bounds for length 0' (AssertionError 'index=0, numBits=0' with -ea), because the check for set bits past the end calls nextSetBit(0) on the zero-length set; the default Bits.applyMask, which the javadoc gives as the reference loop, starts with the same unguarded nextSetBit(0) and throws for an empty destination at every offset.

## Expected vs actual

- Expected: Both calls return normally (an empty window has nothing to clear) and both tests pass.
- Actual: Both tests fail with `java.lang.ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0` from `FixedBitSet.nextSetBit` (`AssertionError: index=0, numBits=0` with assertions on).

## Files

- [src/org/apache/lucene/util/TestApplyMaskEmptyDestination.java](src/org/apache/lucene/util/TestApplyMaskEmptyDestination.java) (JUnit test)

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

# run 1: assertions on, seed A0D17123
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=A0D17123 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestApplyMaskEmptyDestination
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestApplyMaskEmptyDestination.java](TestApplyMaskEmptyDestination.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
