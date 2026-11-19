# FSTCompletionLookup.build leaves an open, empty temp file behind when its second temp file cannot be created: it creates its ExternalRefSorter, whose constructor opens a temp output, before the try block that holds its only cleanup (every release since 6.0.0)

Finding LFV-1493 · Area: suggest / FSTCompletionLookup build (temp outputs of its offline sort) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1493.html](../../bugs/LFV-1493.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an FSTCompletionLookup suggester with a temp Directory, and the directory cannot create the second of the temp files the build asks for, for example because the process has exactly one file descriptor left. You expect the failed build to close and delete the temp file it had already created, as its finally block does when a later step of the build fails (with one gap of its own, LFV-1495) and as the 2012 fix LUCENE-4209 set out to do ('close its sorter, so that it won't leave temp files behind' in the system temp directory). Instead build() throws the IOException before it reaches the try block that holds its only cleanup, so the sorter's temp file &lt;prefix&gt;_RefSorterRaw_N.tmp stays in the directory, empty and with its output still open (until garbage collection on a file-system directory), and no caller can close it; the file stays until someone deletes it by name.

## Expected vs actual

- Expected: `build` throws the `IOException` and the temp directory is empty: `tempDir.listAll()` returns `[]`.
- Actual: `tempDir.listAll()` returns `[suggest_RefSorterRaw_0.tmp]` (`expected:<end of array> but was:<suggest_RefSorterRaw_0.tmp>` at line 49 of the test).

## Files

- [src/org/apache/lucene/search/suggest/fst/TestFSTCompletionLookupFailedBuildTempFile.java](src/org/apache/lucene/search/suggest/fst/TestFSTCompletionLookupFailedBuildTempFile.java) (JUnit test)

## Run it

- Tested on Lucene main ec3b3cca8f10 (2026-11-18): still reproduces.
- Tested on Lucene main 781025d6fe32 (2026-11-13): reproduced.
- What the test does: it wraps a ByteBuffersDirectory in a FilterDirectory whose second createTempOutput call throws IOException('Too many open files'). This injected fault is part of the test: it stands for a temp directory that can create one more file and then no further one. The test constructs FSTCompletionLookup(tempDir, "suggest"), expects build(InputIterator.EMPTY) to throw the IOException, which it does, and then asserts that the temp directory lists no file. It fails because suggest_RefSorterRaw_0.tmp is still listed ('array lengths differed, expected.length=0 actual.length=1'), the same with and without assertions. The test checks the file that is left behind; it does not check that the file's output is still open, and it uses no real file-descriptor limit. No extra heap is needed; beside core the recipe needs the suggest and analysis-common jars, which it lists. The file to compile is the test under src/, as the commands below do; the copy at the top of this folder, the one the finding page shows, is the same file. It carries the Apache license header, and the assertion that fails is at its line 49, the line the finding page quotes. The one other file of the recorded repro, which is not exported, is an earlier copy of this same test without the license header (36 lines; nothing else differs).
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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.fst.TestFSTCompletionLookupFailedBuildTempFile
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestFSTCompletionLookupFailedBuildTempFile.java](TestFSTCompletionLookupFailedBuildTempFile.java)

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
