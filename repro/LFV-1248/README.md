# OfflineSorter.verifyChecksum re-opens the file in its try-with-resources header, so when that re-open fails, the read failure it was called to annotate (an EOFException from a truncated entry, a read I/O error) is dropped and sort() throws only the unrelated open failure, with no cause and no suppressed exception (since 6.0.0)

Finding LFV-1248 · Area: util / OfflineSorter (checksum check after a read failure; used by the suggesters and hunspell) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1248.html](../../bugs/LFV-1248.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort a file with OfflineSorter (directly, or through a suggester build or hunspell offline sorting), reading one of its entries fails (here a truncated entry gives EOFException), and the sorter's follow-up checksum check cannot re-open the same file (here the Directory's second openInput of that name throws IOException). You expect sort() to throw the read failure with the outcome of the checksum check attached, as verifyChecksum's own javadoc says and as CodecUtil.checkFooter(in, priorException) does on every path, even when the checksum cannot be computed. Instead sort() throws only the re-open IOException, with no cause and no suppressed exception, because the re-open sits in the try-with-resources header, before checkFooter ever receives the prior exception.

## Expected vs actual

- Expected: `java.io.EOFException` (the truncated entry), with the checksum outcome or the re-open failure attached as a suppressed exception.
- Actual: `AssertionFailedError: Unexpected exception type, expected EOFException but got java.io.IOException: re-open failed`; that `IOException` has no cause and no suppressed exception.

## Files

- [src/org/apache/lucene/util/TestOfflineSorterReopenFailure.java](src/org/apache/lucene/util/TestOfflineSorterReopenFailure.java) (JUnit test)

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

# run 1: assertions on, seed C01E27A061AEC564
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=C01E27A061AEC564 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestOfflineSorterReopenFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestOfflineSorterReopenFailure.java](TestOfflineSorterReopenFailure.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
