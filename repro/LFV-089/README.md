# CheckIndex silently skips FLOAT16 vector fields: it reports 'OK [0 fields, 0 vectors]' and never runs checkFloat16VectorValues

Bug LFV-089 · Area: index / CheckIndex vectors · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index vectors with KnnFloat16VectorField (default codec) and run CheckIndex at level 2 or higher. You expect it to count and check the FLOAT16 fields the way it does FLOAT32 and BYTE fields; the same 2-field, 3-vector index built with KnnFloatVectorField reports 2 fields / 3 vectors. Instead it prints 'test: vectors.............OK [0 fields, 0 vectors]', because the skip guard in CheckIndex.testVectors tests only the FLOAT32 and BYTE getters, so every FLOAT16 field is skipped and the dedicated checkFloat16VectorValues (iteration, dimension, size and kNN self-search checks) never runs.

The test shows that `CheckIndex` at level 2 (`Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS`) skips a `KnnFloat16VectorField`, so the FLOAT16 vector checks never run.

## Expected vs actual

Expected: 1 vector field and 1 vector checked for an index with one FLOAT16 vector. Actual: `totalKnnVectorFields` = 0 (and `totalVectorValues` = 0).

## Files

- [src/org/apache/lucene/index/TestCheckIndexFloat16Vectors.java](src/org/apache/lucene/index/TestCheckIndexFloat16Vectors.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckIndexFloat16Vectors
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
