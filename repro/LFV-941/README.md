# The file-format javadocs of the default vector formats (Lucene99FlatVectorsFormat and Lucene99HnswVectorsFormat) give a wrong.vemf/.vem/.vex grammar: both metadata lists leave out the int32 vector encoding that is written right after the field number, so every later element is misread, and since 10.3 the.vex list still calls the HNSW neighbour deltas vints although they are group-varint encoded (documentation defect; the writers and readers agree)

Bug LFV-941 · Area: codecs / vectors (Lucene99FlatVectorsFormat.vemf and Lucene99HnswVectorsFormat.vem and.vex file-format javadoc vs their writers and readers) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode the vector metadata (.vemf,.vem) or the HNSW graph (.vex) files of the default codec by following the Lucene99FlatVectorsFormat and Lucene99HnswVectorsFormat javadocs, which list each field as '[int32] field number, [int32] vector similarity function ordinal, [vlong] offset,...' and each neighbour list as '[vint] count, array[vint] delta-encoded ordinals'. Both writers instead store an extra int32, the vector encoding (BYTE or FLOAT32), between the field number and the similarity, and since 10.3 they write the neighbour deltas with group-varint, so a parser built from the text reads the encoding as the similarity (a COSINE float field comes out as DOT_PRODUCT), gets the offset, length, dimension and count wrong, and decodes wrong neighbours from the first node on. Lucene's own readers match the writers, so nothing breaks at runtime; only the documentation is wrong, the metadata part since 9.4.0 and the.vex part since 10.3.0.

The test shows that the vector metadata files of the default codec (.vemf of Lucene99FlatVectorsFormat,.vem of Lucene99HnswVectorsFormat), parsed with their javadoc lists ([int32] field number, [int32] vector similarity function ordinal,...), misread the similarity: both writers put an undocumented int32 vector encoding between the field number and the similarity.

## Expected vs actual

Similarity ordinal COSINE (2) in both files; actual 1 in both (the FLOAT32 encoding ordinal, which reads as DOT_PRODUCT).

## Files

- [src/org/apache/lucene/codecs/lucene99/TestLucene99VectorsFormatsMetaJavadoc.java](src/org/apache/lucene/codecs/lucene99/TestLucene99VectorsFormatsMetaJavadoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene99.TestLucene99VectorsFormatsMetaJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
