# If MergePolicy.keepFullyDeletedSegment throws while IndexWriter opens an NRT reader (for example a read error while SoftDeletesRetentionMergePolicy runs its retention query), StandardDirectoryReader.open never releases the SegmentReader it obtained just before the check, so that segment's core and files stay open after IndexWriter.close

Bug LFV-626 · Area: core / index NRT reader open from IndexWriter (StandardDirectoryReader.open(IndexWriter,...) keep check for fully deleted segments; SoftDeletesRetentionMergePolicy as the stock trigger) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use soft deletes with SoftDeletesRetentionMergePolicy, open a new IndexWriter on an index whose last commit holds a fully soft-deleted segment that the retention query keeps, and call DirectoryReader.open(writer); the merge policy's keep check for that segment fails (one read error while the retention query reads the segment, or your retention-query supplier throws), so the open fails with that exception. You expect the failed open to release what it acquired, as StandardDirectoryReader.open's own catch block does for every reader already in its list (LUCENE-5262), so that closing the writer leaves no file open. Instead the SegmentReader obtained just before the keep check is neither in that list nor released, so after IndexWriter.close the segment stays open (MockDirectoryWrapper: 'there are still 4 open files: {_0.cfs=1, _0_1_Lucene90_0.dvp=1, _0_1_Lucene90_0.dvs=1, _0_1_Lucene90_0.dvd=1}'), while the writer itself stays open and a later NRT open works.

## Expected vs actual

- Expected: You expect the failed open to release what it acquired, as StandardDirectoryReader.open's own catch block does for every reader already in its list (LUCENE-5262), so that closing the writer leaves no file open.
- Actual: The SegmentReader obtained just before the keep check is neither in that list nor released, so after IndexWriter.close the segment stays open (MockDirectoryWrapper: 'there are still 4 open files: {_0.cfs=1, _0_1_Lucene90_0.dvp=1, _0_1_Lucene90_0.dvs=1, _0_1_Lucene90_0.dvd=1}'), while the writer itself stays open and a later NRT open works.

## Files

- [src/org/apache/lucene/index/TestNrtOpenKeepFullyDeletedFailureLeaksReader.java](src/org/apache/lucene/index/TestNrtOpenKeepFullyDeletedFailureLeaksReader.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.index.TestNrtOpenKeepFullyDeletedFailureLeaksReader
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
