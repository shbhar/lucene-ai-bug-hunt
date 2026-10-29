# The file-format javadoc of the default compound format (Lucene90CompoundFormat) gives an incomplete.cfe entry-table grammar: it leaves out the codec footer the writer appends (the.cfs line beside it lists one), and it says each entry holds the file's name although the writer stores the name with the segment name stripped (documentation defect; the writer and reader agree)

Bug LFV-971 · Area: codecs / compound files (Lucene90CompoundFormat.cfe file-format javadoc vs the Lucene90CompoundFormat writer / Lucene90CompoundReader) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode the compound entry table (.cfe) of a default-codec segment by following the Lucene90CompoundFormat javadoc, which says the file is Header, FileCount and then FileCount entries of FileName, DataOffset, DataLength, each holding 'a String with that file's name'. The writer also appends a 16-byte codec footer (the.cfs line just above lists its Footer, the.cfe line does not) and stores each name with the segment name stripped, for example '.nvd' for _0.nvd, so a parser built from the text ends 16 bytes early (byte 502 of 518 in the repro) and cannot find the sub-files by the names it read. Lucene's own reader checks the footer and puts the segment name back, so nothing breaks at runtime; only the documentation is wrong (the type line also lists an unused 'Checksum', which belongs to the linked CodecFooter grammar, so dropping it is cleanup).

The test shows that the `.cfe` grammar in the `Lucene90CompoundFormat` class javadoc does not match what the writer writes: it leaves out the trailing Footer, and FileName is stored without the segment name (a documentation defect; the writer and reader agree).

## Expected vs actual

Expected (javadoc): nothing after the last entry, and FileName = the file's name. Actual: `bytes after the documented grammar expected:<0> but was:<16>` and `FileName values expected:<[_0.fdm, _0.fdt, _0.fdx, _0.fnm]> but was:<[.fdm,.fdt,.fdx,.fnm]>` (-ea and -da).

## Files

- [src/org/apache/lucene/codecs/lucene90/TestLucene90CompoundEntriesJavadoc.java](src/org/apache/lucene/codecs/lucene90/TestLucene90CompoundEntriesJavadoc.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 2 test(s) run, 2 failed.
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
     execute --select-class org.apache.lucene.codecs.lucene90.TestLucene90CompoundEntriesJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/codec-format-javadoc-grammar-overflow-liv-cfe-extension-table/](../../models/codec-format-javadoc-grammar-overflow-liv-cfe-extension-table/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
