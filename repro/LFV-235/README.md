# Closing a FilterCodecReader runs the wrapped reader's close hook directly (in.doClose instead of in.close), so a DirectoryReader that still uses that segment silently finds 0 hits, and its own close later releases the segment a second time and throws

Bug LFV-235 · Area: index / FilterCodecReader reader lifecycle (IndexReader reference counting) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-235.html](../../bugs/LFV-235.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a segment of an open DirectoryReader in a FilterCodecReader (for example to pass it to IndexWriter.addIndexes), take the usual extra reference with incRef, and close the wrapper, expecting the DirectoryReader to keep working, because the IndexReader javadoc says a reader 'will not really be closed until decRef has been called for all outstanding references' and FilterLeafReader behaves exactly that way. Instead FilterCodecReader.doClose calls the wrapped reader's internal close hook (in.doClose) rather than releasing a reference (in.close), so the segment's files are closed while the DirectoryReader still uses it: a TermQuery that matched 1 document now finds 0 with no error, reading stored fields throws AlreadyClosedException, and closing the DirectoryReader later releases the same resources a second time and throws.

The test shows that closing a `FilterCodecReader` over an incRef'd segment of an open `DirectoryReader` runs the segment's `doClose()` directly, so the still-open `DirectoryReader` silently loses the segment's terms.

## Expected vs actual

Expected `count(id:1)` = 1 (the DirectoryReader still holds a reference). Actual: 0, with no exception.

## Files

- [src/org/apache/lucene/index/TestFilterCodecReaderCloseKeepsReferencedReader.java](src/org/apache/lucene/index/TestFilterCodecReaderCloseKeepsReferencedReader.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestFilterCodecReaderCloseKeepsReferencedReader
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
