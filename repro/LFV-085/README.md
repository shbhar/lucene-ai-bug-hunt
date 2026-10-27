# With raw vectors dropped from a Lucene104 scalar-quantized field, rescorer.bulk reads quantized bytes as float32 vectors and returns wrong scores

Bug LFV-085 · Area: codecs / vectors · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You drop the raw float vectors from a Lucene104 scalar-quantized field (the read-only fallback added by #15415), open the index with MMapDirectory and the Panama vector provider, and score documents with FloatVectorValues#rescorer(q).bulk(...). You expect the same scores as rescorer(q).score, which compares the query with each document's dequantized vector. Instead bulk reads the quantized.veq bytes as if they were float32 vectors at the wrong stride and returns, for example, 8.9e-16 instead of 0.84, or NaN, 0.0 or huge values such as 5.3e37; with the 1-bit and 2-bit encodings it throws instead (IndexOutOfBoundsException from bulk, or IllegalArgumentException when the rescorer is created).

## Expected vs actual

- Expected: You expect the same scores as rescorer(q).score, which compares the query with each document's dequantized vector.
- Actual: bulk reads the quantized.veq bytes as if they were float32 vectors at the wrong stride and returns, for example, 8.9e-16 instead of 0.84, or NaN, 0.0 or huge values such as 5.3e37; with the 1-bit and 2-bit encodings it throws instead (IndexOutOfBoundsException from bulk, or IllegalArgumentException when the rescorer is created).

## Files

- [src/org/apache/lucene/codecs/lucene104/TestDroppedRawRescorerBulk.java](src/org/apache/lucene/codecs/lucene104/TestDroppedRawRescorerBulk.java) (JUnit test)
- [src2/org/apache/lucene/codecs/lucene104/TestDroppedRawRescorerBulk.java](src2/org/apache/lucene/codecs/lucene104/TestDroppedRawRescorerBulk.java) (JUnit test)

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

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
