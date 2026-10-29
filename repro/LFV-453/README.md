# IndexWriter.updateDocuments / softUpdateDocuments with an empty block leave about 33 KB of phantom RAM accounting at every refresh or commit that drops the empty per-thread writer, so ramBytesUsed grows without bound and, once it passes the RAM buffer, every later document is flushed into its own segment

Bug LFV-453 · Area: index / IndexWriter flush-control RAM accounting (DocumentsWriterFlushControl.markForFullFlush drops a 0-document DocumentsWriterPerThread; reached through updateDocuments and softUpdateDocuments with an empty block) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter.updateDocuments(term, List.of), or softUpdateDocuments(term, List.of, softField), to delete a key's documents and add nothing, then refresh or commit; you expect the delete to apply and nothing to stay buffered, so ramBytesUsed returns to 0 as it does after deleteDocuments(term) or addDocuments(List.of). Instead the call took a per-thread writer and charged its delete buffer (about 33 KB) to the flush control, and the next full flush throws that 0-document writer away without taking the charge back, so ramBytesUsed climbs by about 33 KB per round and never comes down (20 rounds with a default config: 660,800 B). Once the phantom total passes the RAM buffer (about 510 rounds at the default 16 MB) the writer flushes after every document, so 200 small documents become 200 segments; with assertions enabled the writer closes itself with an AssertionError instead.

The test shows that `IndexWriter.updateDocuments(term, List.of())` (an empty block) followed by one NRT reopen leaves RAM charged to the writer although nothing is buffered.

## Expected vs actual

Expected `ramBytesUsed()` 0 after the refresh (as after `deleteDocuments(term)`); actual 33,040 (`expected:<0> but was:<33040>`), with `-ea` (plus a suppressed `netBytes has unexpected value 0 != 33040` on close) and `-da`.

## Files

- [src/org/apache/lucene/index/TestEmptyBlockUpdateRamAccounting.java](src/org/apache/lucene/index/TestEmptyBlockUpdateRamAccounting.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestEmptyBlockUpdateRamAccounting
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
