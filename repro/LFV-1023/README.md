# On main (11.0), the BpVectorReorderer command line cannot reorder an index created by Lucene 9.x or 10.x, or one with a soft-deletes field: it rewrites the index through a default IndexWriterConfig (created major 11, no soft-deletes field), so the first addIndexes throws IllegalArgumentException

Bug LFV-1023 · Area: misc / vector BP reordering (BpVectorReorderer command line: in-place rewrite through IndexWriter.addIndexes) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run BpVectorReorderer.main(path, field) on an index written by Lucene 10.x or 9.x, or on any index that has a soft-deletes field, and expect it to reorder the vectors in place: its javadoc takes 'a path containing an index to reorder', and on main indexes created with 9.x and 10.x 'remain fully supported (open and write)'. Instead reorderIndexDirectory opens a new IndexWriter with new IndexWriterConfig and OpenMode.CREATE, which records created major 11 and no soft-deletes field, so the first addIndexes(SortingCodecReader.wrap(leaf,...)) throws IllegalArgumentException 'Cannot merge a segment that has been created with major version 10 into this index which has been created by major version 11' (or 'soft-deletes field is not configured in IWC'). The javadoc of IndexWriterConfig.setIndexCreatedVersionMajor names exactly this addIndexes restriction and its remedy (use the source's created major), which the tool does not apply.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: reorderIndexDirectory opens a new IndexWriter with new IndexWriterConfig and OpenMode.CREATE, which records created major 11 and no soft-deletes field, so the first addIndexes(SortingCodecReader.wrap(leaf,...)) throws IllegalArgumentException 'Cannot merge a segment that has been created with major version 10 into this index which has been created by major version 11' (or 'soft-deletes field is not configured in IWC').

## Files

- [src/org/apache/lucene/misc/index/TestBpVectorReorderEarlierMajorIndex.java](src/org/apache/lucene/misc/index/TestBpVectorReorderEarlierMajorIndex.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBpVectorReorderEarlierMajorIndex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
