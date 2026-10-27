# Opening an index created by a newer Lucene major throws IllegalArgumentException ('indexCreatedVersionMajor is in the future') instead of IndexFormatTooNewException

Bug LFV-120 · Area: index / SegmentInfos commit read (forward version check) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open, with an older Lucene, an index that a newer Lucene major created, for example Lucene 9.12.3 opening an untouched index written by 10.5.2 with DirectoryReader.open or new IndexWriter. You expect IndexFormatTooNewException, whose javadoc says it 'is thrown when Lucene detects an index that is newer than this Lucene version', which is what you get when the newer release also bumped the segments_N format number. Instead SegmentInfos.readCommit hands the on-disk created major to the public SegmentInfos(int) constructor, whose argument check throws an unchecked IllegalArgumentException 'indexCreatedVersionMajor is in the future: 10', with a suppressed CorruptIndexException that says 'checksum passed... possibly transient resource issue, or a Lucene or JVM bug'.

## Expected vs actual

- Expected: You expect IndexFormatTooNewException, whose javadoc says it 'is thrown when Lucene detects an index that is newer than this Lucene version', which is what you get when the newer release also bumped the segments_N format number.
- Actual: SegmentInfos.readCommit hands the on-disk created major to the public SegmentInfos(int) constructor, whose argument check throws an unchecked IllegalArgumentException 'indexCreatedVersionMajor is in the future: 10', with a suppressed CorruptIndexException that says 'checksum passed... possibly transient resource issue, or a Lucene or JVM bug'.

## Files

- [src/org/apache/lucene/index/TestOpenIndexFromNewerMajor.java](src/org/apache/lucene/index/TestOpenIndexFromNewerMajor.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.index.TestOpenIndexFromNewerMajor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
