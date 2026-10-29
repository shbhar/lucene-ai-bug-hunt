# BufferingKnnVectorsWriter hands writeField vector values whose ordToDoc(ord) returns ord, which is wrong for sparse fields and, with an index sort, for every field (latent: no shipped writer calls it)

Bug LFV-298 · Area: codecs / BufferingKnnVectorsWriter flush-time vector values (KnnVectorValues.ordToDoc and getAcceptOrds) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a vector codec on BufferingKnnVectorsWriter and, in writeField, ask the flush-time vector values which document a vector ordinal belongs to (ordToDoc, or getAcceptOrds, which is built on it), expecting what the javadoc promises, 'the docid of the document indexed with the given vector ordinal'. Instead the buffered values answer ordToDoc(ord) = ord, which is right only when every document has a vector and there is no index sort: for a field that documents 0, 2 and 3 have, ordinals 1 and 2 map to documents 1 and 2 instead of 2 and 3, so a getAcceptOrds filter that accepts only document 2 accepts document 3's vector and one that accepts only document 3 accepts none; under a reversing index sort even a dense field's ordinals map to themselves instead of to their documents' sorted positions. Iteration (nextDoc, index, vectorValue) stays correct, nothing throws, and FLOAT32, BYTE and FLOAT16 behave the same.

The test shows that the vector values BufferingKnnVectorsWriter passes to writeField at flush answer ordToDoc(ord) = ord, which is wrong for a field that not every document has.

## Expected vs actual

Expected each docID-&gt;ordToDoc(index()) pair for documents 0, 2, 3 to be `[0->0, 2->2, 3->3]`; actual `[0->0, 2->1, 3->2]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/codecs/TestBufferingKnnVectorsWriterOrdToDoc.java](src/org/apache/lucene/codecs/TestBufferingKnnVectorsWriterOrdToDoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.TestBufferingKnnVectorsWriterOrdToDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
