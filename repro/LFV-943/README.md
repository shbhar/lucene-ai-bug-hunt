# The SegmentInfos class javadoc gives a wrong segments_N (commit point) grammar: IndexCreatedVersionMajor, SoftDelCount, the SegmentCommitInfo id and FieldInfosFiles are missing, NameCounter is documented as an Int32 but written as a VLong, the fixed-width integers are written big-endian although the javadoc links the little-endian DataOutput#writeInt/#writeLong, and SegID is called the codec's identifier (documentation defect; the writer and reader agree)

Bug LFV-943 · Area: index / SegmentInfos (segments_N file-format javadoc vs its writer and reader) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode a segments_N commit file by following the SegmentInfos class javadoc, which gives the order Header, LuceneVersion, Version, NameCounter, SegCount, MinSegmentLuceneVersion, then per segment SegName, SegID, SegCodec, DelGen, DeletionCount, FieldInfosGen, DocValuesGen, UpdatesFiles, DocValuesOverlays, and types NameCounter as an Int32 and the fixed-width fields with links to DataOutput#writeInt/#writeLong (little-endian since 9.0). The writer instead puts an IndexCreatedVersionMajor VInt right after LuceneVersion, writes NameCounter as a VLong, writes Version, SegCount, DelGen, DeletionCount and the other fixed-width fields big-endian, and adds SoftDelCount, a flag byte plus the 16-byte SegmentCommitInfo id and the FieldInfosFiles set to every segment entry, so a parser built from the text reads the VInt 11 where Version should be and every later field is misaligned. Lucene's own reader matches the writer, so nothing breaks at runtime; only the documentation is wrong, and it has not matched the bytes since 4.9.0 (2014).

The test shows that the segments_N grammar in the `SegmentInfos` class javadoc is stale: the field written right after LuceneVersion is the undocumented IndexCreatedVersionMajor vInt, not Version (documentation defect; writer and reader agree).

## Expected vs actual

Expected (javadoc): Version, an Int64 equal to `SegmentInfos#getVersion` (2 for an empty commit). Actual: `Version expected:<2> but was:<11>`, where 11 is the IndexCreatedVersionMajor vInt (same with -ea and -da).

## Files

- [src/org/apache/lucene/index/TestSegmentsNJavadocVersionField.java](src/org/apache/lucene/index/TestSegmentsNJavadocVersionField.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestSegmentsNJavadocVersionField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
