# With reader pooling off, an IOException while IndexWriter opens a segment reader to apply buffered deletes leaks the reader-pool reference that IndexWriter.openSegmentStates took for it, so the segment's ReadersAndUpdates, and the SegmentReader and open files a later apply opens for it, stay in the pool until the segment is merged away or the writer closes (every release since 4.0.0)

Finding LFV-1288 · Area: index / IndexWriter buffered deletes and updates resolution (reader pool references) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1288.html](../../bugs/LFV-1288.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You turn reader pooling off (IndexWriterConfig.setReaderPooling(false)) and delete a document; while one flush() applies that delete, opening the segment's reader fails with an IOException, so that flush throws, the writer stays open, and another flush or commit applies the delete successfully. You expect the writer to give back every reader-pool reference it took, so that, as without the I/O error, the segment is no longer pooled once the delete is applied and committed. Instead IndexWriter.openSegmentStates never releases the reference it took for the SegmentState whose constructor threw: the segment stays in the pool with refCount 2, and the SegmentReader (with its open files) that the next apply opens is kept until the segment is merged away or the writer closes.

## Expected vs actual

- Expected: After the commit `_0` is no longer in the reader pool (`getPooledInstance(_0, false)` is null), as in the same run without the I/O error.
- Actual: `AssertionError: expected null, but was:<ReadersAndLiveDocs(seg=_0(12.0.0):c2 ...>`: `_0` is still pooled, with refCount 2.

## Files

- [src/org/apache/lucene/index/TestApplyDeletesOpenFailureReleasesReader.java](src/org/apache/lucene/index/TestApplyDeletesOpenFailureReleasesReader.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestApplyDeletesOpenFailureReleasesReader
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestApplyDeletesOpenFailureReleasesReader.java](TestApplyDeletesOpenFailureReleasesReader.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
