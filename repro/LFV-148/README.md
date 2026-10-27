# (Latent) MonotonicBlockPackedWriter writes a block whose values end close to Long.MAX_VALUE with too few bits, so MonotonicBlockPackedReader returns wrong values: min + (long) (avg * i) overflows in flush

Bug LFV-148 · Area: util / packed ints (MonotonicBlockPackedWriter, MonotonicBlockPackedReader) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a block of non-negative, increasing longs with MonotonicBlockPackedWriter, for example {1, Long.MAX_VALUE}, and read it back with MonotonicBlockPackedReader. You expect the same values back: the writer is documented as 'A writer for large monotonically increasing sequences of positive longs' with no upper bound, and flush says 'adjust min so that all deltas will be positive'. Instead the predicted value min + (long) (avg * i) wraps past Long.MAX_VALUE, so the adjustment of min is skipped and the resulting negative delta is ignored when the bit width is chosen; get(1) returns Long.MIN_VALUE with no error (a larger block trips an AssertionError in BulkOperationPacked.encode under -ea and reads back wrong values under -da).

## Expected vs actual

- Expected: You expect the same values back: the writer is documented as 'A writer for large monotonically increasing sequences of positive longs' with no upper bound, and flush says 'adjust min so that all deltas will be positive'.
- Actual: The predicted value min + (long) (avg * i) wraps past Long.MAX_VALUE, so the adjustment of min is skipped and the resulting negative delta is ignored when the bit width is chosen; get(1) returns Long.MIN_VALUE with no error (a larger block trips an AssertionError in BulkOperationPacked.encode under -ea and reads back wrong values under -da).

## Files

- [src/org/apache/lucene/util/packed/TestMonotonicBlockPackedNearMaxValue.java](src/org/apache/lucene/util/packed/TestMonotonicBlockPackedNearMaxValue.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.packed.TestMonotonicBlockPackedNearMaxValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/packed-ints-monotonic/](../../models/packed-ints-monotonic/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
