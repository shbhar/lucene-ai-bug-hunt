# COSINE vectors whose float32 squared norm underflows pass validation and give Inf/NaN scores or poison OSQ segments

Bug LFV-046 · Area: codecs / vectors · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-046.html](../../bugs/LFV-046.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index, or query with, a non-zero COSINE float vector whose float32 squared norm is 0 or subnormal. You expect it to be rejected like an all-zero vector or to score sensibly. Instead it is accepted: on the default codec it scores +Inf/NaN, a NaN batch maximum hides 64 neighbouring documents and CheckIndex falsely fails; on OSQ one such vector makes the segment centroid NaN, so kNN returns 0 hits and every merge fails.

## Expected vs actual

- Expected: `{1e-30, 0}` is rejected like `{0, 0}`, or every COSINE score stays in [0, 1] and kNN, `CheckIndex` and merges work.
- Actual: accepted; scores +Infinity, NaN and 1.1103; tiny is the top hit; 0 hits for `{0, 1}`; `CheckIndex` (level 2) fails; OSQ returns 0 hits and `forceMerge` closes the writer.

## Files

- [src/TestF5CosineUnderflow.java](src/TestF5CosineUnderflow.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
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
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Df5.dir=mmap -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestF5CosineUnderflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestCosineTinyNormValidation.java](TestCosineTinyNormValidation.java)
- [cosine-tiny-norm-validation.diff](cosine-tiny-norm-validation.diff)
- [test.diff](test.diff)

Formal models and scripts this bug's part of the hunt used: [../../models/study-quantization-smt/](../../models/study-quantization-smt/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
