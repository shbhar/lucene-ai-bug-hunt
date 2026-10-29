# A document rejected for a custom TermFrequencyAttribute on a field with term-vector positions or offsets, postings positions or DOCS-only postings is rejected inside an aborting step of the indexing chain, so it closes the IndexWriter and rolls back every change since the last commit

Bug LFV-281 · Area: index / IndexingChain inversion with custom term frequencies (TermVectorsConsumerPerField, FreqProxTermsWriterPerField) x aborting-exception classification · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a document whose analysis chain sets a custom term frequency (TermFrequencyAttribute, for example DelimitedTermFrequencyTokenFilter with a token like 'red|2') on a field that also stores term-vector positions or offsets, indexes postings positions (a default TextField), or is DOCS-only and repeats the term. You expect addDocument to reject only that document with its usual IllegalArgumentException or IllegalStateException and the writer to stay open, as it does for the neighbouring offset checks and for the duplicate-term check that #15584 made non-aborting. Instead the four checks run inside termsHashPerField.add, which IndexingChain treats as aborting, so the rejection also becomes the writer's tragic exception: IndexWriter closes itself, the next call throws AlreadyClosedException, and everything since the last commit is lost (the repro ends with 1 committed document instead of 3).

The test shows that on main a document rejected by addDocument for a custom term frequency (TermFrequencyAttribute 2 on a DOCS_AND_FREQS field with term-vector positions) also closes the IndexWriter as a tragic event.

## Expected vs actual

Expected: `IllegalArgumentException` for that document only and `getTragicException() == null`. Actual: the same `IllegalArgumentException` ('cannot index term vector positions while using custom TermFrequencyAttribute') is the writer's tragic exception.

## Files

- [src/org/apache/lucene/index/TestCustomTermFreqRejectionKeepsWriterOpen.java](src/org/apache/lucene/index/TestCustomTermFreqRejectionKeepsWriterOpen.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestCustomTermFreqRejectionKeepsWriterOpen
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
