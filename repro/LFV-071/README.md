# Panama minMaxScalarQuantize rounds differently from the default implementation at one boundary value

Finding LFV-071 · Area: codecs / vectors · Priority: low · Impact: minor · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-071.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-071.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You scalar-quantize vectors with the Panama vector provider. You expect the same bytes as the default implementation. Instead Panama rounds with (int)fma(x, scale, 0.5f), not Math.round, so the two can differ only when fl(scale*x) == 0.49999997f.

The test shows that with the Panama vector provider (&gt;= 256-bit vectors), `ScalarQuantizer(0, 1, 8).quantize` rounds a value whose scaled offset is 0.49999997f up to byte 1, where Math.round (the default provider, the "nearest byte") gives 0.

## Expected vs actual

Expected: dest[0] == 0; actual (-ea and -da, preferredBitSize=256): `expected:<0> but was:<1>`; on 128-bit hosts or without the vector module the test passes.

## Files

- [src/org/apache/lucene/util/quantization/TestScalarQuantizerNearestByte.java](src/org/apache/lucene/util/quantization/TestScalarQuantizerNearestByte.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (a commit of 2026-11-06): still reproduces.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- This test needs a CPU with 256-bit or wider vectors: on a CPU with 128-bit vectors it passes, because Lucene's vectorized loop does not run there. The re-runs on Lucene main 781025d6fe32 (2026-11-13) and 9965d34e3c77 (2026-11-16) and on the 11.0 branch (branch_11x 9d2b5a15427a) were made on a CPU with 128-bit vectors, where the test as shipped passes, with and without assertions, and so with a small adaptation: the package line changed to org.apache.lucene.internal.vectorization, the ScalarQuantizer line deleted, the quantizer.quantize(...) call replaced by VectorizationProvider.lookup(true).getVectorUtilSupport().minMaxScalarQuantize(vector, dest, 255f, 1f / 255f, 0f, 1f), and -Dtests.vectorsize=256 added to the java command (which then selects the class in its new package). The adapted test calls Lucene's internal vectorized method directly, with the arguments the public method passes, and Lucene's test switch tests.vectorsize makes the JVM run it with 256-bit vectors; so adapted, the test fails 1 of 1 with the same message (expected:&lt;0&gt; but was:&lt;1&gt;), with and without assertions, on each of these builds and with the 10.6.0 release jars, and without the switch it passes.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces (with an adapted copy of the repro).
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
     execute --select-class org.apache.lucene.util.quantization.TestScalarQuantizerNearestByte
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/study-simd-equivalence/](../../models/study-simd-equivalence/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
