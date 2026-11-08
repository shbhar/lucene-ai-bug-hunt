# After one failed close of a doc-values producer, SegmentDocValues keeps the closed producer in its per-generation cache, so a reader later reopened onto a commit that uses that doc-values generation is handed the closed producer and every doc-values read on it throws AlreadyClosedException

Finding LFV-1217 · Area: index / SegmentDocValues (per-generation doc-values producer cache shared by the SegmentReaders of one segment core; reopen with openIfChanged(reader, commit)) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1217.html](../../bugs/LFV-1217.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You keep older commits, open a reader r1 on a commit whose segment has doc-values updates, reopen it onto an older commit as r2 (r2 shares r1's segment core but not the updated doc-values generation), and closing r1 fails with an I/O error from that generation's doc-values input. You expect r2, and any reader later opened from it, to keep working: openIfChanged(r2, newer commit) should return a usable reader of that commit, just as a fresh open of the commit does. Instead SegmentDocValues closed the producer before removing it from its cache and RefCount put the reference back when the close threw, so the reopened reader is handed the closed producer and its first doc-values read throws AlreadyClosedException ('Already closed.').

## Expected vs actual

- Expected: `r3 = openIfChanged(r2, commit 1)` reads `n` = 10 for doc 0, as a fresh open of commit 1 does.
- Actual: `r3`'s first `getNumericDocValues("n")` throws `AlreadyClosedException: Already closed.`

## Files

- [src/org/apache/lucene/index/TestReopenAfterFailedDocValuesClose.java](src/org/apache/lucene/index/TestReopenAfterFailedDocValuesClose.java) (JUnit test)

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

# run 1: assertions on, seed C0FFEE42
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=C0FFEE42 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestReopenAfterFailedDocValuesClose
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestReopenAfterFailedDocValuesClose.java](TestReopenAfterFailedDocValuesClose.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
