# The example native int8 dot product (lucene/misc/src/c/dotProduct.c, opt-in via -Dlucene.useNativeDotProduct=true) keeps its SVE loop index in int32, so VectorUtil.dotProduct(byte, byte) on two arrays of at least 2^31 - 4*VL elements (2,147,483,520 at 256-bit SVE, 2,147,483,584 at 128-bit SVE) overflows the loop bound, reads past the end of both arrays and kills the JVM with SIGSEGV in vdot8s_sve

Bug LFV-390 · Area: core vectorization + misc native C / opt-in native int8 dot product (NativeVectorUtilSupport, lucene/misc/src/c/dotProduct.c vdot8s_sve) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable Lucene's opt-in native dot product (-Dlucene.useNativeDotProduct=true plus a library built from the example lucene/misc/src/c/dotProduct.c) on an SVE machine and call VectorUtil.dotProduct(byte, byte) on two legal arrays of 2,147,483,645 bytes whose exact dot product is 3, expecting 3 as the Panama and default providers return. Instead the SVE kernel vdot8s_sve keeps its loop index and vector length in int32_t, so its loop test 'i + 4 * vec_length &lt;= limit' (dotProduct.c:66) overflows once i reaches 2^31 - 4*VL (VL = SVE vector length in bytes); in the compiled code the bound wraps negative while the load offset is a 64-bit register, so the loop keeps loading past the end of both arrays until it hits an unmapped page and the JVM dies with SIGSEGV in vdot8s_sve. Every length from 2^31 - 4*VL up to the JVM maximum crashes (2,147,483,520 at 256-bit SVE, 2,147,483,584 at 128-bit SVE), while one element less returns the exact result; a 64-bit loop index with svwhilelt_b8_s64 fixes it.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The SVE kernel vdot8s_sve keeps its loop index and vector length in int32_t, so its loop test 'i + 4 * vec_length &lt;= limit' (dotProduct.c:66) overflows once i reaches 2^31 - 4*VL (VL = SVE vector length in bytes); in the compiled code the bound wraps negative while the load offset is a 64-bit register, so the loop keeps loading past the end of both arrays until it hits an unmapped page and the JVM dies with SIGSEGV in vdot8s_sve.

## Files

- [src/NativeDotProductNearMaxLength.java](src/NativeDotProductNearMaxLength.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- Not runnable from this site as is: this repro also needs extra JVM setup (such as an instrumentation hook) that is not exported here; see the comments at the top of the source.

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
java -ea -esa -Xmx6g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dlucene.useNativeDotProduct=true -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" NativeDotProductNearMaxLength
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
