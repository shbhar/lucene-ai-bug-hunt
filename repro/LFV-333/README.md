# MemoryIndex ignores TermFrequencyAttribute, so FeatureField values and custom term frequencies are indexed as frequency 1 and their postings frequencies, term statistics, norms and scores differ from IndexWriter's (Lucene Monitor's single-document matching included)

Bug LFV-333 · Area: memory / MemoryIndex term inversion (custom term frequencies: FeatureField, TermFrequencyAttribute streams, DOCS_AND_CUSTOM_FREQS); reached in-tree through monitor single-document batches · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-333.html](../../bugs/LFV-333.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a document with FeatureField("features", "pagerank", 10f), or a tokenized DOCS_AND_FREQS field whose TokenStream sets TermFrequencyAttribute (for example DelimitedTermFrequencyTokenFilter on 'a|7'), to a MemoryIndex and expect its postings, statistics and scores to equal those of the same document in a one-document IndexWriter index, as they do for ordinary fields. Instead MemoryIndex.storeTerms never reads the attribute and counts every token as frequency 1: FeatureField.newLinearQuery scores 4.5918E-41 instead of 10.0, newSaturationQuery with the pivot 10 scores exactly 0.0 (MemoryIndex.search's 'no match' value) for a document that matches, and a term with custom frequency 7 reports freq 1, sumTotalTermFreq 1 and a lower BM25 score. Lucene Monitor inherits it (since 8.6.0): the same FeatureField document scores 10.0 in a two-document batch (IndexWriter path) and 4.5918E-41 when matched alone (MemoryIndex path).

## Expected vs actual

- Expected: The `MemoryIndex` reports what the one-document `IndexWriter` index reports: freq 33344 and score 10.0 for `FeatureField` 10f, saturation score 0.5, freq 7 and BM25 0.2456 for the custom frequency.
- Actual: Freq 1 everywhere: linear score 4.5918E-41, saturation score 0.0 ("no match") for a matching document, and freq 1, `sumTotalTermFreq` 2, BM25 0.1308 for the custom frequency.

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexCustomTermFrequency.java](src/org/apache/lucene/index/memory/TestMemoryIndexCustomTermFrequency.java) (JUnit test)
- [src/org/apache/lucene/index/memory/TestMemoryIndexTermDocCustomFrequency.java](src/org/apache/lucene/index/memory/TestMemoryIndexTermDocCustomFrequency.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexCustomTermFrequency \
       --select-class org.apache.lucene.index.memory.TestMemoryIndexTermDocCustomFrequency
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestMemoryIndexCustomTermFrequencies.java](TestMemoryIndexCustomTermFrequencies.java)
- [TestMonitorFeatureFieldSingleDocument.java](TestMonitorFeatureFieldSingleDocument.java)
- [TestMemoryIndexCustomTermFrequency.java](TestMemoryIndexCustomTermFrequency.java)
- [TestMemoryIndexCustomTermFreq.java](TestMemoryIndexCustomTermFreq.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
