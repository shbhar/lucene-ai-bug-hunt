# IndexSplitter.split publishes a durable commit in the destination index without fsyncing the segment files it copied, so an OS crash or power loss soon after a split can leave a destination whose newest commit references empty files and cannot be opened

Bug LFV-191 · Area: misc / index tools (IndexSplitter.split, SegmentInfos.commit durability) · Priority: low · Impact: index unusable · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You copy segments into a new index with IndexSplitter (new IndexSplitter(src).split(dest, segments), or the command line 'IndexSplitter &lt;src&gt; &lt;dest&gt; &lt;segments&gt;'), and the machine crashes or loses power shortly after the call returns. You expect what every Lucene commit is built for (Directory.sync javadoc: Lucene syncs files 'to prevent a machine/OS crash from corrupting the index'; IndexWriter.commit syncs all referenced files first): the destination's commit only references files whose bytes are on stable storage. Instead split copies the segment files with Files.copy and never fsyncs them, and SegmentInfos.commit then fsyncs only the new segments_N and the directory, so the commit and the copied file names can survive the crash while the copied.si,.cfs and.cfe data does not, and opening the destination fails (CorruptSegmentInfoException on _1.si on main, CorruptIndexException on 10.5.2 and 9.12.3, in the simulated post-crash state).

The test shows that `IndexSplitter.split` publishes a commit in the destination without fsyncing the segment files it copied (only `pending_segments_N` and the directory are forced).

## Expected vs actual

Expected: every file the split's commit references has been fsynced (Directory.sync contract); actual: `segment files of the split's commit never fsynced: [_0.cfe, _0.cfs, _0.si]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/misc/index/TestIndexSplitterFsync.java](src/org/apache/lucene/misc/index/TestIndexSplitterFsync.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestIndexSplitterFsync
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
