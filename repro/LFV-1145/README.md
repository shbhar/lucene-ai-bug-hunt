# With the Panama vector provider, NumericDocValues.rangeIntoBitSet on a dense default-codec field sets bits for a reversed, and therefore empty, doc range [fromDoc, toDoc) with fromDoc &gt; toDoc, where the scalar provider and every other implementation set none (since 10.5.0)

Finding LFV-1145 · Area: codecs / doc values bulk range scan (Lucene90DocValuesProducer dense paths -> internal vectorization PanamaDocValuesRangeSupport) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1145.html](../../bugs/LFV-1145.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 32 documents with NumericDocValuesField values 0..31 on the default codec (a dense field), run the JVM with the vector incubator module enabled, and call NumericDocValues.rangeIntoBitSet(20, 13, Long.MIN_VALUE, Long.MAX_VALUE, bits, 0); you expect no bit to be set, because the javadoc defines the result as the doc IDs in [fromDoc, toDoc), which is empty here, and the scalar provider, the default per-doc loop and Lucene90's own sorted-numeric overrides all set nothing. With the Panama provider doc 12 is set, with no error under -ea or -da. PanamaDocValuesRangeSupport.rangeIntoBitSet has no empty-range guard: loopBound = fromDoc + LONG_SPECIES.loopBound(toDoc - fromDoc) rounds the negative length down to a multiple of the lane count and lands below toDoc, the SIMD loop is skipped, and the scalar tail tests and sets the docs in [loopBound, toDoc) that were never requested (for [10, 5) with 4 lanes: docs 2, 3 and 4).

## Expected vs actual

- Expected: `bits.cardinality()` is 0: `[12, 5)` contains no doc, as the scalar provider gives.
- Actual: `expected:<0> but was:<1>`: doc 4 is marked, with assertions on and off, and no error.

## Files

- [src/org/apache/lucene/index/TestReversedRangeIntoBitSet.java](src/org/apache/lucene/index/TestReversedRangeIntoBitSet.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestReversedRangeIntoBitSet
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestReversedRangeIntoBitSet.java](TestReversedRangeIntoBitSet.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
