# With raw vectors dropped from a Lucene104 scalar-quantized field and the index opened with MMapDirectory and the Panama vector provider, rescorer().bulk() reads quantized bytes as float32 vectors and returns wrong scores, and a search with RescoreTopNQuery.createFullPrecisionRescorerQuery throws IllegalArgumentException for the 4-bit, 2-bit and 1-bit encodings from 34, 23 and 20 dimensions

Finding LFV-085 · Area: codecs / vectors · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You drop the raw float vectors from a Lucene104 scalar-quantized field (the read-only fallback added by #15415), open the index with MMapDirectory and the Panama vector provider, and score documents with FloatVectorValues#rescorer(q).bulk(...). You expect the same scores as rescorer(q).score(), which compares the query with each document's dequantized vector. Instead bulk() reads the quantized .veq bytes as if they were float32 vectors at the wrong stride and returns, for example, 8.9e-16 instead of 0.84, or NaN, 0.0 or huge values such as 5.3e37; depending on the encoding, the dimension and the number of documents it throws instead: IndexOutOfBoundsException from bulk(), or, with the 4-bit, 2-bit and 1-bit encodings from 34, 23 and 20 dimensions, IllegalArgumentException when the rescorer is created, which a search with RescoreTopNQuery.createFullPrecisionRescorerQuery then throws too.

## Expected vs actual

- Expected: You expect the same scores as rescorer(q).score(), which compares the query with each document's dequantized vector.
- Actual: bulk() reads the quantized .veq bytes as if they were float32 vectors at the wrong stride and returns, for example, 8.9e-16 instead of 0.84, or NaN, 0.0 or huge values such as 5.3e37; depending on the encoding, the dimension and the number of documents it throws instead: IndexOutOfBoundsException from bulk(), or, with the 4-bit, 2-bit and 1-bit encodings from 34, 23 and 20 dimensions, IllegalArgumentException when the rescorer is created, which a search with RescoreTopNQuery.createFullPrecisionRescorerQuery then throws too.

## Files

- [src/org/apache/lucene/codecs/lucene104/TestDroppedRawRescorerBulk.java](src/org/apache/lucene/codecs/lucene104/TestDroppedRawRescorerBulk.java) (JUnit test)
- [src2/org/apache/lucene/codecs/lucene104/TestDroppedRawRescorerBulk.java](src2/org/apache/lucene/codecs/lucene104/TestDroppedRawRescorerBulk.java) (JUnit test)

## Run it

- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- The folder holds the same test twice. src/ is for Lucene 10.5.0 and later (10.5.x, 10.6.0, the 11.0 release branch, main); src2/ is for 10.4.0, where the enum ScalarEncoding is a nested class of Lucene104ScalarQuantizedVectorsFormat. Each compiles only on its own versions: on 10.5.0 and later leave out the two src2 lines (javac and java), on 10.4.0 the two src lines. On its own versions the test fails 1 of 3: testRescorerBulkMatchesScoreAfterDroppingRawVectors fails (expected 0.8403607 but was 8.920945E-16) and the two controls pass (run on 2026-11-17 with and without assertions: src/ on main 9965d34e3c77, branch_11x 9d2b5a15427a and 10.6.0, src2/ on 10.4.0). The test covers the wrong bulk scores; the exceptions named above have no test in this folder.
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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene104.TestDroppedRawRescorerBulk
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene104.TestDroppedRawRescorerBulk
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
