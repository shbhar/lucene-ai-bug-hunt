# CheckIndex with more than one thread (threadCount &gt; 1, the default on multi-core machines) sorts the segments with a size comparator that returns 0 whenever SegmentCommitInfo.sizeInBytes() throws, so on an index with 32 or more segments and one missing segment file checkIndex() can throw IllegalArgumentException 'Comparison method violates its general contract!' instead of returning a Status that reports the broken segment

Finding LFV-1244 · Area: index / CheckIndex concurrent segment check (size sort of the commit's segments) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1244.html](../../bugs/LFV-1244.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run CheckIndex on an index of 32 segments in which one segment's stored-fields data file (.fdt) has gone missing, with the default thread count on a multi-core machine (or setThreadCount(2)). You expect a Status that reports that one segment as broken (numBadSegments = 1, with the NoSuchFileException as its error), as the same check with one thread returns, so that you can then exorcise it. Instead checkIndex() throws java.lang.IllegalArgumentException 'Comparison method violates its general contract!' from the sort that orders the segments by size before the concurrent check: its comparator calls sizeInBytes(), which throws for the damaged segment on every call, and then answers 'equal', so that segment is equal to every other segment, the order is not consistent, and the exception escapes checkIndex().

## Expected vs actual

- Expected: A `Status` with `numBadSegments == 1` naming `_k` (`NoSuchFileException: _k.fdt`), as the same index gives with one thread.
- Actual: `IllegalArgumentException: Comparison method violates its general contract!` at `CheckIndex.checkIndex(CheckIndex.java:858)`; no `Status`.

## Files

- [src/org/apache/lucene/index/TestCheckIndexConcurrentMissingFile.java](src/org/apache/lucene/index/TestCheckIndexConcurrentMissingFile.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckIndexConcurrentMissingFile
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCheckIndexConcurrentMissingFile.java](TestCheckIndexConcurrentMissingFile.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
