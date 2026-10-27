# A float vector field routed to HnswBitVectorsFormat is rejected inside an aborting step of the indexing chain, so the one rejected document closes the IndexWriter and rolls back every document added since the last commit

Bug LFV-121 · Area: codecs / HnswBitVectorsFormat x index / IndexingChain aborting-exception classification · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use HnswBitVectorsFormat, which accepts only byte vectors, and by mistake add a document with a float vector field (KnnFloatVectorField) that is new to the index. You expect addDocument to reject that one document with IllegalArgumentException and the writer to stay open, as the format's own test TestHnswBitVectorsFormat#testFloatVectorFails expects the exception and as happens for the same codec's dimension limit and for a vector dimension mismatch. Instead the check sits in KnnVectorsWriter#addField, which IndexingChain calls after registering the field and treats as an aborting step, so the exception also becomes the writer's tragic exception: IndexWriter closes itself, the next call throws AlreadyClosedException, and the documents added since the last commit are gone (the repro ends with 1 committed document instead of 3).

## Expected vs actual

- Expected: You expect addDocument to reject that one document with IllegalArgumentException and the writer to stay open, as the format's own test TestHnswBitVectorsFormat#testFloatVectorFails expects the exception and as happens for the same codec's dimension limit and for a vector dimension mismatch.
- Actual: The check sits in KnnVectorsWriter#addField, which IndexingChain calls after registering the field and treats as an aborting step, so the exception also becomes the writer's tragic exception: IndexWriter closes itself, the next call throws AlreadyClosedException, and the documents added since the last commit are gone (the repro ends with 1 committed document instead of 3).

## Files

- [src/org/apache/lucene/codecs/bitvectors/TestBitVectorsFloatFieldClosesWriter.java](src/org/apache/lucene/codecs/bitvectors/TestBitVectorsFloatFieldClosesWriter.java) (JUnit test)
- [src/org/apache/lucene/codecs/bitvectors/TestBitVectorsFloatFieldClosesWriterPortable.java](src/org/apache/lucene/codecs/bitvectors/TestBitVectorsFloatFieldClosesWriterPortable.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.bitvectors.TestBitVectorsFloatFieldClosesWriter

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.bitvectors.TestBitVectorsFloatFieldClosesWriterPortable
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
