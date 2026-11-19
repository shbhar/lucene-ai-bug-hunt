# With LogDocMergePolicy, the megabyte limit of CompoundFormat#setMaxCFSSegmentSizeMB (MergePolicy#setMaxCFSSegmentSizeMB from 4.4.0 to 10.x, LogMergePolicy#setMaxCFSSegmentSizeMB in 4.0.0 to 4.3.1) is compared with the merged segment's document count, so a merged segment larger than the limit is still written as a compound file (file layout only; in every release since 4.0.0)

Finding LFV-1497 · Area: codecs / compound-file decision for merged segments (CompoundFormat.useCompoundFile: the maximum segment size under LogDocMergePolicy; MergePolicy.useCompoundFile up to 10.x) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1497.html](../../bugs/LFV-1497.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use LogDocMergePolicy and tell Lucene not to pack large merged segments into a compound (.cfs) file, for example with setMaxCFSSegmentSizeMB(1). You expect a merged segment larger than 1 MB to be written as separate files, as the setter's javadoc says ('If a merged segment will be larger than this value, it will be left as a non-compound file even if compound files are enabled') and as the byte-size merge policies do. Instead the merged segment is still written as a compound file however large it is, because Lucene compares the limit, converted to bytes, with the segment's number of documents, so a 1 MB limit acts as a limit of 1,048,576 documents; search results are not affected, only the file layout and one extra copy of the segment at merge time.

## Expected vs actual

- Expected: The merged segment (2,098,992 bytes, above the 1 MB limit) is not a compound file.
- Actual: It is a compound file: `AssertionError: a merged segment of 2098992 bytes is a compound file` at line 54, on main and on the 11.0 branch.

## Files

- [src/org/apache/lucene/index/TestMaxCFSSegmentSizeWithLogDocMergePolicy.java](src/org/apache/lucene/index/TestMaxCFSSegmentSizeWithLogDocMergePolicy.java) (JUnit test)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: it sets setMaxCFSSegmentSizeMB(1) on the default codec's compound format, uses LogDocMergePolicy with the serial merge scheduler on a ByteBuffersDirectory, flushes two segments of one document each, each document with one 1 MiB binary doc-values field, and calls forceMerge(1). It then reads the commit, checks that the one merged segment is larger than 1 MiB and asserts that it is not a compound file. The test fails with 'a merged segment of 2098992 bytes is a compound file', the same with and without assertions; it uses no randomness. It needs the CompoundFormat API of branch_11x and main, so it does not compile against a release. The file that is not exported is the earlier form of this test; it differs in the wording of one comment line only.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMaxCFSSegmentSizeWithLogDocMergePolicy
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestMaxCFSSegmentSizeWithLogDocMergePolicy.java](TestMaxCFSSegmentSizeWithLogDocMergePolicy.java)

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
