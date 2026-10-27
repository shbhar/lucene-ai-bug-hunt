# BufferingKnnVectorsWriter hands writeField vector values whose ordToDoc(ord) returns ord, which is wrong for sparse fields and, with an index sort, for every field (latent: no shipped writer calls it)

Bug LFV-298 · Area: codecs / BufferingKnnVectorsWriter flush-time vector values (KnnVectorValues.ordToDoc and getAcceptOrds) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a vector codec on BufferingKnnVectorsWriter and, in writeField, ask the flush-time vector values which document a vector ordinal belongs to (ordToDoc, or getAcceptOrds, which is built on it), expecting what the javadoc promises, 'the docid of the document indexed with the given vector ordinal'. Instead the buffered values answer ordToDoc(ord) = ord, which is right only when every document has a vector and there is no index sort: for a field that documents 0, 2 and 3 have, ordinals 1 and 2 map to documents 1 and 2 instead of 2 and 3, so a getAcceptOrds filter that accepts only document 2 accepts document 3's vector and one that accepts only document 3 accepts none; under a reversing index sort even a dense field's ordinals map to themselves instead of to their documents' sorted positions. Iteration (nextDoc, index, vectorValue) stays correct, nothing throws, and FLOAT32, BYTE and FLOAT16 behave the same.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The buffered values answer ordToDoc(ord) = ord, which is right only when every document has a vector and there is no index sort: for a field that documents 0, 2 and 3 have, ordinals 1 and 2 map to documents 1 and 2 instead of 2 and 3, so a getAcceptOrds filter that accepts only document 2 accepts document 3's vector and one that accepts only document 3 accepts none; under a reversing index sort even a dense field's ordinals map to themselves instead of to their documents' sorted positions.

## Files

- [src/org/apache/lucene/codecs/TestBufferedVectorValuesOrdToDoc.java](src/org/apache/lucene/codecs/TestBufferedVectorValuesOrdToDoc.java) (JUnit test)
- [src2/org/apache/lucene/codecs/TestBufferedVectorValuesOrdToDoc.java](src2/org/apache/lucene/codecs/TestBufferedVectorValuesOrdToDoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.TestBufferedVectorValuesOrdToDoc
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.TestBufferedVectorValuesOrdToDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
