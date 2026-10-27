# addIndexes(CodecReader...) with the default config rejects inputs whose maxDoc sum, deleted documents included, exceeds MAX_DOCS although only their live documents are added, because it always builds a merged view of the inputs for OneMerge#reorder (regression since 9.9.0)

Bug LFV-285 · Area: index / IndexWriter addIndexes(CodecReader...) (addIndexesReaderMerge merged view: SlowCompositeCodecReaderWrapper over a MultiReader, BaseCompositeReader doc limit) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter.addIndexes(CodecReader...) with readers that have many deleted or filtered-out documents, so their live documents fit under IndexWriter.MAX_DOCS but their maxDoc values (deleted documents included) add up to more. You expect the call to succeed, since addIndexes counts and reserves only live documents, its javadoc throws IllegalArgumentException only if the index would exceed MAX_DOCS, and 9.8.0 accepted the same call. Instead, with the default configuration it throws IllegalArgumentException 'Too many documents: composite IndexReaders cannot exceed...' and adds nothing, because since 9.9.0 addIndexesReaderMerge always wraps the inputs in a SlowCompositeCodecReaderWrapper (a MultiReader over the uncompacted inputs) for OneMerge#reorder, whose default returns null and discards the view.

## Expected vs actual

- Expected: You expect the call to succeed, since addIndexes counts and reserves only live documents, its javadoc throws IllegalArgumentException only if the index would exceed MAX_DOCS, and 9.8.0 accepted the same call.
- Actual: With the default configuration it throws IllegalArgumentException 'Too many documents: composite IndexReaders cannot exceed...' and adds nothing, because since 9.9.0 addIndexesReaderMerge always wraps the inputs in a SlowCompositeCodecReaderWrapper (a MultiReader over the uncompacted inputs) for OneMerge#reorder, whose default returns null and discards the view.

## Files

- [src/org/apache/lucene/index/TestAddIndexesReadersWithManyDeletions.java](src/org/apache/lucene/index/TestAddIndexesReadersWithManyDeletions.java) (JUnit test)
- [src/FullScaleAddIndexes.java](src/FullScaleAddIndexes.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.index.TestAddIndexesReadersWithManyDeletions

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" FullScaleAddIndexes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/codecreader-wrappers/](../../models/codecreader-wrappers/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
