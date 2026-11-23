# In no release (10.6.0 does not have it; 11.0.0 would be the first): on the 11.0 branch and main, a java.lang.Error (for example an OutOfMemoryError) thrown while SegmentInfos writes the commit file leaves pending_segments_N in the directory, because the close helper in the catch block rethrows the Error before the delete runs (10.6.0 deletes the file); the next commit made without an IndexWriter from a freshly read SegmentInfos, before any IndexWriter opens the directory, then fails once with FileAlreadyExistsException and the attempt after it works (no committed data is touched), the same one-time failure that 10.6.0 and 9.12.3 show when such a stale file is already there (the runs put it there by hand; by the code, a JVM that dies in the middle of a commit leaves one)

Finding LFV-1509 · Area: index / commit file write (SegmentInfos.write(Directory), the first half of every commit; seen through commits made without an IndexWriter: CheckIndex.exorciseIndex and misc IndexSplitter.remove) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1509.html](../../bugs/LFV-1509.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

In no release: 10.6.0 does not have it and 11.0.0 would be the first release with it (it is on branch_11x and main). A commit is being written and a java.lang.Error (in practice an OutOfMemoryError) is thrown at one of the few statements with which SegmentInfos creates, writes, closes and syncs the new commit file pending_segments_N, once that file exists. You expect the failed commit to remove that file, as 10.6.0 and every earlier release tried (back to 6.6.6) do after such an Error (the comment at the site: 'Try not to leave a truncated segments_N file in the index'). Instead, on the 11.0 branch and on main the file stays: readers ignore it and the next IndexWriter deletes it when it opens, but a commit made without an IndexWriter from a freshly read SegmentInfos before that (a second run of CheckIndex with exorcise, or of IndexSplitter's remove option) fails once with FileAlreadyExistsException, removes the leftover while failing, and works on the run after; no committed data is touched, and 10.6.0 and 9.12.3 fail the same way once when such a stale file is already in the directory (the runs put it there by hand; by the code, a JVM that dies in the middle of a commit leaves one).

## Expected vs actual

- Expected: The directory is empty after the failed commit (`listAll()` returns 0 names), as on 10.6.0 and with the fix.
- Actual: `listAll()` returns 1 name: `array lengths differed, expected.length=0 actual.length=1; ... expected:<end of array> but was:<pending_segments_1>` (line 39).

## Files

- [src/org/apache/lucene/index/TestSegmentInfosCommitErrorLeavesPendingFile.java](src/org/apache/lucene/index/TestSegmentInfosCommitErrorLeavesPendingFile.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- In no release: the test fails on Lucene main and on the 11.0 release branch (branch_11x daa54f2e0cd3), with and without assertions, and passes on the 10.6.0 release jars, so 11.0.0 would be the first release with it. What the test does: it calls SegmentInfos.commit on an empty in-memory directory whose sync throws an OutOfMemoryError, expects that Error, and then expects the directory to be empty, as it is on 10.6.0. On main and branch_11x the new commit file stays, so the test fails with 'array lengths differed, expected.length=0 actual.length=1; arrays first differed at element [0]; expected:&lt;end of array&gt; but was:&lt;pending_segments_1&gt;'. What the leftover means: readers ignore it and the next IndexWriter deletes it when it opens; only a commit made without an IndexWriter from a freshly read SegmentInfos before that (CheckIndex with exorcise, IndexSplitter's remove option) fails once with FileAlreadyExistsException, removes the leftover while failing, and works on the attempt after; no committed data is touched. 10.6.0 and 9.12.3 fail the same way once when such a stale file is already in the directory (the runs put it there by hand; by the code, a JVM that dies in the middle of a commit leaves one). The test uses no randomness.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSegmentInfosCommitErrorLeavesPendingFile
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSegmentInfosCommitErrorLeavesPendingFile.java](TestSegmentInfosCommitErrorLeavesPendingFile.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
