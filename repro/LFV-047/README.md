# Two antipodal COSINE vectors make the OSQ centroid zero, so flush or merge fails and the writer closes

Bug LFV-047 · Area: codecs / vectors · Priority: medium · Impact: index unusable · Novelty: new

Bug page: [../../bugs/LFV-047.html](../../bugs/LFV-047.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

With the scalar-quantized (OSQ) vectors format and COSINE, you index vectors whose normalized sum is exactly zero, such as {1,0} and {-1,0}, into one segment. You expect flush and merge to work. Instead normalizing the zero centroid throws 'Cannot normalize a zero-length vector', the writer closes tragically with its buffered documents lost, and merges stay blocked.

The test shows that with `Lucene104ScalarQuantizedVectorsFormat` and COSINE, committing the two vectors `{1,0}` and `{-1,0}` fails, because their unit-scaled mean (the centroid) is exactly zero.

## Expected vs actual

Expected: the commit succeeds, as it does with the default vectors format. Actual: flush throws `IllegalArgumentException: Cannot normalize a zero-length vector` from `computeCentroid` (`VectorUtil.l2normalize` on the zero centroid).

## Files

- [src/org/apache/lucene/codecs/lucene104/TestOsqAntipodalZeroCentroid.java](src/org/apache/lucene/codecs/lucene104/TestOsqAntipodalZeroCentroid.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene104.TestOsqAntipodalZeroCentroid
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-quantization-smt/](../../models/study-quantization-smt/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
