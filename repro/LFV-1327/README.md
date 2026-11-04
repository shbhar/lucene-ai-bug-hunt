# A codec file whose extension contains '_' (IndexFileNames.segmentFileName(si.name, "", "my_ext") = "_0.my_ext", accepted by CODEC_FILE_PATTERN) is recorded by SegmentInfo as the non-existent "_0_ext", so the first flush throws NoSuchFileException and closes the IndexWriter as tragic, because IndexFileNames.indexOfSegmentName takes a '_' after the first '.' as the end of the segment name (since 5.0.0)

Finding LFV-1327 · Area: index / segment file naming (IndexFileNames.indexOfSegmentName, SegmentInfo.namedForThisSegment) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1327.html](../../bugs/LFV-1327.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a custom codec whose format also writes one per-segment file named with IndexFileNames.segmentFileName(si.name, "", "my_ext"), which gives "_0.my_ext", a name that CODEC_FILE_PATTERN (checked by SegmentInfo) accepts, then add one document and commit. You expect the commit to succeed and the segment's files() to list "_0.my_ext", as it does for the extension "myext". Instead SegmentInfo records the file as "_0_ext", because IndexFileNames.indexOfSegmentName takes the '_' inside the extension as the end of the segment name, so the flush throws NoSuchFileException: _0_ext and the IndexWriter closes itself as tragic, losing the document.

## Expected vs actual

- Expected: `commit()` succeeds and the segment lists `_0.my_ext`, as it lists `_0.myext` for the extension `myext`.
- Actual: `java.nio.file.NoSuchFileException: _0_ext` from `SegmentCommitInfo.sizeInBytes`, and the IndexWriter closes itself.

## Files

- [src/org/apache/lucene/index/TestUnderscoreInCodecFileExtension.java](src/org/apache/lucene/index/TestUnderscoreInCodecFileExtension.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestUnderscoreInCodecFileExtension
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestUnderscoreInCodecFileExtension.java](TestUnderscoreInCodecFileExtension.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
