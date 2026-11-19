# ExternalRefSorter.close() does not close its temp output when the footer write fails, so an FSTCompletionLookup.build that fails on a full disk keeps a file descriptor on a deleted temp file until garbage collection (since 6.0.0)

Finding LFV-1495 · Area: suggest / FSTCompletionLookup build (ExternalRefSorter: cleanup of the sorter's temp output when a build fails) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1495.html](../../bugs/LFV-1495.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an FSTCompletionLookup suggester and the build fails because the disk under its temp directory is full (a write throws IOException). You expect the failed build to close every temp output it opened: build closes its sorter in a finally block, and ExternalRefSorter.close() is documented as 'Close the writer and remove any temporary files'. Instead close() first writes a checksum footer to the sorter's temp output; on the full disk that write fails too, so the output is never closed and build swallows this second error: the caller sees only the first IOException, while the output on the already deleted &lt;prefix&gt;_RefSorterRaw_N.tmp stays open until the garbage collector reclaims it (one file descriptor per failed build on a real file system). LFV-1493 is the other gap in the cleanup of the same build: a temp-file creation that fails before the cleanup block is entered.

## Expected vs actual

- Expected: `build` throws `IOException` and leaves no temp output open, so `tempDir.close()` returns.
- Actual: `tempDir.close()` throws `MockDirectoryWrapper: cannot close: there are still 1 open files: {suggest_RefSorterRaw_0.tmp=1}` (line 34), with assertions on and off.

## Files

- [src/org/apache/lucene/search/suggest/fst/TestFSTCompletionLookupFailedBuildOpenOutput.java](src/org/apache/lucene/search/suggest/fst/TestFSTCompletionLookupFailedBuildOpenOutput.java) (JUnit test)

## Run it

- Tested on Lucene main ec3b3cca8f10 (2026-11-18): still reproduces.
- Tested on Lucene main 781025d6fe32 (2026-11-13): reproduced.
- What the test does: it takes the test framework's MockDirectoryWrapper as the temp directory and sets its size limit to one byte (setMaxSizeInBytes(1)), so that writes fail as on a full disk. This injected fault is part of the test; the same wrapper keeps count of open files. The test constructs FSTCompletionLookup(tempDir, "suggest"), expects build(InputIterator.EMPTY) to throw an IOException, which it does, and then closes the directory. It fails there with 'MockDirectoryWrapper: cannot close: there are still 1 open files: {suggest_RefSorterRaw_0.tmp=1}': the failed build left the sorter's temp output open. The outcome is the same with and without assertions. The test does not measure file descriptors or garbage collection on a real file system. No extra heap is needed; beside core the recipe needs the suggest and analysis-common jars, which it lists. The file to compile is the test under src/, as the commands below do; the copy at the top of this folder, the one the finding page shows, is the same file. It carries the Apache license header, and the tempDir.close() call that fails is at its line 34, the line the finding page quotes. The one other file of the recorded repro, which is not exported, is an earlier copy of this same test without the license header (20 lines; nothing else differs).
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.fst.TestFSTCompletionLookupFailedBuildOpenOutput
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestFSTCompletionLookupFailedBuildOpenOutput.java](TestFSTCompletionLookupFailedBuildOpenOutput.java)

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
