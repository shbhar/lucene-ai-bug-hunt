# The file-format javadoc of the default norms format (Lucene90NormsFormat) gives a wrong.nvm grammar: each Entry omits the Int16 jump-table entry count and the byte dense rank power that the writer stores right after DocsWithFieldLength, so a parser that follows it misreads NumDocsWithField and every later field; the grammar line is also labelled '.dvm' (documentation defect; the writer and reader agree)

Bug LFV-938 · Area: codecs / norms (Lucene90NormsFormat.nvm file-format javadoc vs Lucene90NormsConsumer / Lucene90NormsProducer) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode the norms metadata (.nvm) file of the default codec by following the Lucene90NormsFormat javadoc, which says each Entry is FieldNumber (Int32), DocsWithFieldAddress (Int64), DocsWithFieldLength (Int64), NumDocsWithField (Int32), BytesPerNorm (byte), NormsAddress (Int64). Since the jump tables of Lucene 8.0 the writer stores two more fields right after DocsWithFieldLength, an Int16 jump-table entry count and a byte dense rank power, so a parser built from the text reads NumDocsWithField as 67108863 instead of 3 (the bytes FF FF and FF of the two missing fields plus the first byte of the real count) and every later field is shifted. Lucene's own reader matches the writer, so nothing breaks at runtime; only the documentation is wrong (it also labels the file '.dvm', the doc-values extension).

The test shows that the norms metadata file (.nvm) of the default codec, parsed with the Lucene90NormsFormat javadoc Entry (FieldNumber, DocsWithFieldAddress, DocsWithFieldLength, NumDocsWithField,...), misreads NumDocsWithField, because the writer puts an Int16 jump-table entry count and a byte dense rank power right after DocsWithFieldLength.

## Expected vs actual

NumDocsWithField 3 (three documents, each with a norm); actual 67108863 (0x03FFFFFF: the undocumented FF FF and FF plus the first byte of the real count).

## Files

- [src/org/apache/lucene/codecs/lucene90/TestLucene90NormsMetaJavadoc.java](src/org/apache/lucene/codecs/lucene90/TestLucene90NormsMetaJavadoc.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.lucene90.TestLucene90NormsMetaJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
