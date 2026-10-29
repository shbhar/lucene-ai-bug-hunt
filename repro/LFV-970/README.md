# The file-format javadoc of the default live-docs format (Lucene90LiveDocsFormat) gives the.liv grammar as 'IndexHeader,Generation,Bits', but no separate Generation has been written since LUCENE-5969 (the deletes generation is the IndexHeader suffix), the codec footer the writer appends is missing, and the type list defines SegmentHeader instead of IndexHeader (documentation defect; the writer and reader agree)

Bug LFV-970 · Area: codecs / live docs (Lucene90LiveDocsFormat.liv file-format javadoc vs its writer and reader) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode a live-docs (.liv) file of the default codec by following the Lucene90LiveDocsFormat javadoc, which gives the layout 'IndexHeader, Generation, Bits'. The writer instead stores the deletes generation inside the IndexHeader (as its suffix, in base 36), then the bit words, then a 16-byte codec footer, so there is no Generation field after the header: a decoder that reads one there gets the first word of the bit set, and one that skips it finds 16 bytes the grammar does not mention. The type list also defines 'SegmentHeader' while the grammar says 'IndexHeader'; Lucene's own reader matches the writer, so nothing breaks at runtime, only the documentation is wrong.

The test shows that the `Lucene90LiveDocsFormat` javadoc grammar `IndexHeader,Generation,Bits` is stale, because the deletes generation is written only as the IndexHeader suffix, so the value after the header is the first Bits word.

## Expected vs actual

Expected: the element after the IndexHeader is the generation, 1. Actual: `Generation expected:<1> but was:<2>` (2 is the Bits word 0b10, doc 0 deleted; every integer type reads 2 here).

## Files

- [src/org/apache/lucene/codecs/lucene90/TestLucene90LiveDocsFormatJavadoc.java](src/org/apache/lucene/codecs/lucene90/TestLucene90LiveDocsFormatJavadoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene90.TestLucene90LiveDocsFormatJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/codec-format-javadoc-grammar-overflow-liv-cfe-extension-table/](../../models/codec-format-javadoc-grammar-overflow-liv-cfe-extension-table/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
