# The file-format javadoc of the default field infos format (Lucene94FieldInfosFormat) gives a wrong.fnm grammar: the per-field sequence leaves out IndexOptions, the doc-values skip-index byte, PointIndexDimensionCount and the three vector bytes, and the value tables miss written values (doc-values codes 4 and 5, FieldBits 0x8 and 0x10, IndexOptions 5 since 10.5.0, VectorEncoding 2 on main) (documentation defect; the writer and reader agree)

Bug LFV-942 · Area: codecs / field infos (Lucene94FieldInfosFormat.fnm file-format javadoc vs its writer and reader) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode a field infos (.fnm) file of the default codec by following the Lucene94FieldInfosFormat javadoc, which gives each field as FieldName, FieldNumber, FieldBits, DocValuesBits, DocValuesGen, Attributes, DimensionCount, DimensionNumBytes, documents DocValuesBits as a norms nibble plus a doc-values nibble with codes 0..3, and lists only FieldBits 0x1, 0x2 and 0x4. The writer instead puts an IndexOptions byte right after FieldBits, writes a single doc-values code (SORTED_SET = 4, SORTED_NUMERIC = 5) followed by a skip-index byte, writes PointIndexDimensionCount and PointNumBytes only when the dimension count is non-zero, appends VectorDimension, VectorEncoding and VectorSimilarityFunction, and sets FieldBits 0x8 (soft-deletes field) and 0x10 (parent field), so a parser built from the text reads the IndexOptions byte of a text field (3) as 'SORTED doc values' and loses alignment on the first field. Lucene's own reader matches the writer, so nothing breaks at runtime; only the documentation is wrong, and its sequence has been since 5.0.0.

The test shows that a.fnm written by the default codec for one TextField, parsed with the per-field sequence of the Lucene94FieldInfosFormat javadoc (FieldName, FieldNumber, FieldBits, DocValuesBits,...), misreads the first field: the byte at the documented DocValuesBits position is the IndexOptions byte the sequence leaves out.

## Expected vs actual

DocValuesBits doc-values nibble 0 (the field has no doc values); actual 3 (IndexOptions DOCS_AND_FREQS_AND_POSITIONS, which the documented nibble table reads as SortedDocValues).

## Files

- [src/org/apache/lucene/codecs/lucene94/TestLucene94FieldInfosFormatJavadoc.java](src/org/apache/lucene/codecs/lucene94/TestLucene94FieldInfosFormatJavadoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene94.TestLucene94FieldInfosFormatJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
