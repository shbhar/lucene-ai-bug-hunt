# KnnVectorValues.fromDISI's advance leaves index stale, so on Lucene 10 sparse byte vectors in segments written by Lucene 9.4 read back as zeros or as another document's vector

Bug LFV-237 · Area: index / vector values iteration (KnnVectorValues.fromDISI) with backward-codecs / Lucene 9.4 sparse byte vector reader · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-237.html](../../bugs/LFV-237.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open, on Lucene 10, an index whose segment was written by Lucene 9.4.2 with a byte vector field that only some documents have, move the field's iterator to a document with advance(doc) and read vectorValue(iterator.index), which is what ByteKnnVectorFieldSource does, and expect that document's own vector, because DocIndexIterator.index is documented as the ordinal of the current document and DocIdSetIterator.advance must behave like repeated nextDoc calls. Instead every document that has a vector reads as all zeros: the iterator that KnnVectorValues.fromDISI builds, which this one reader returns, counts the ordinal only in nextDoc, and its advance moves to the document without moving the ordinal, so index stays at -1 (the reader then hands back its never-filled buffer); after earlier nextDoc calls, index is an earlier document's ordinal and the read returns that document's vector. Lucene 9.x reads the same index correctly; the defect came with the 10.0 vector-values API change #13779.

The test shows that on main, a sparse byte vector in a segment written by Lucene 9.4.2 reads back as zeros when the iterator is positioned with `advance(doc)` and the vector is read with `vectorValue(iterator.index())` (`KnnVectorValues.fromDISI`'s `advance()` leaves `index()` at -1).

## Expected vs actual

Expected doc 1's vector `[1, 2]`; actual `[0, 0]` (`expected:<[[1, 2]]> but was:<[[0, 0]]>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/backward_codecs/lucene94/TestLucene94SparseByteVectorAdvance.java](src/org/apache/lucene/backward_codecs/lucene94/TestLucene94SparseByteVectorAdvance.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.backward_codecs.lucene94.TestLucene94SparseByteVectorAdvance
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
