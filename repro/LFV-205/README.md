# NRTCachingDirectory.sync creates a commit's cached segment files in the underlying directory after the commit's directory fsync, so an OS crash right after the segments_N rename can leave a newest commit whose files are missing and an index that will not open

Bug LFV-205 · Area: store / NRTCachingDirectory (commit durability: IndexWriter.startCommit, SegmentInfos.prepareCommit/finishCommit) · Priority: low · Impact: index unusable · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index through NRTCachingDirectory (for example new NRTCachingDirectory(FSDirectory.open(path), 5.0, 60.0), the class javadoc's own example), call IndexWriter.commit, and the machine crashes during that commit. You expect what Lucene's commit protocol and LUCENE-8048 are built for: after the crash the index opens to the previous commit or to the new one, because SegmentInfos.prepareCommit fsyncs the directory before the commit is published by renaming pending_segments_N to segments_N. Instead the newly flushed segment files are still in NRTCachingDirectory's RAM cache at that directory fsync; NRTCachingDirectory.sync creates them on disk only afterwards and fsyncs their bytes but never the directory, so the rename can become durable before the new files' names do, and in that crash state every open fails (CorruptSegmentInfoException: segment info file _1.si cannot be read), while the same steps on a plain FSDirectory open fine.

The test shows that NRTCachingDirectory creates a commit's cached segment files in the delegate during sync(), after the commit's directory fsync, so a crash state where the segments_2 rename persisted but those creates did not cannot be opened.

## Expected vs actual

Expected: the crash state of the second commit opens at commit 1 or 2. Actual: DirectoryReader.open throws CorruptSegmentInfoException (_1.si missing), with -ea and -da.

## Files

- [src/org/apache/lucene/store/TestNRTCachingDirectoryCommitCrash.java](src/org/apache/lucene/store/TestNRTCachingDirectoryCommitCrash.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestNRTCachingDirectoryCommitCrash
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
