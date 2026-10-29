# ParallelLeafReader.termVectors no longer honours 'the first reader having the field wins'

Bug LFV-018 · Area: index / readers · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-018.html](../../bugs/LFV-018.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap several readers in a ParallelLeafReader where more than one has term vectors for the same field. You expect term vectors from the first reader that has the field, as documented. Instead, since #14373, they come from the wrong reader, and an index written from it with addIndexes fails CheckIndex.

The test shows that `ParallelLeafReader.termVectors()` returns a term vector from the second reader for a field that the first reader owns ("first reader having a given field wins") and indexes without term vectors.

## Expected vs actual

Expected: no term vector for `body` (getFieldInfos() and terms() come from the first reader, which has none). Actual: the second reader's vector (term `second`).

## Files

- [src/org/apache/lucene/index/TestParallelLeafReaderTermVectorsOwner.java](src/org/apache/lucene/index/TestParallelLeafReaderTermVectorsOwner.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestParallelLeafReaderTermVectorsOwner
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
