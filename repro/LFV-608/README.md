# DOCS_AND_CUSTOM_FREQS fields (10.5.0+): the norm length counts terms but sumTotalTermFreq and totalTermFreq sum the custom frequencies, contradicting the FieldInfo#isTermDocField, IndexOptions and FieldStats javadocs and putting BM25's dl and avgdl in different units

Bug LFV-608 · Area: core index / DOCS_AND_CUSTOM_FREQS (termdoc) field length vs term statistics; search similarities (BM25, SimilarityBase) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index two documents, 'a|5 b|5' and 'a|5 b|5 c|5 d|5', into a field with IndexOptions.DOCS_AND_CUSTOM_FREQS (DelimitedTermFrequencyTokenFilter, a TextField type with norms). Lucene stores their lengths as term counts, 2 and 4. FieldInfo#isTermDocField says such frequencies count as 1 'for statistical and scoring purposes', and FieldStats says sumTotalTermFreq 'is also the sum of each document's field length'. So you expect sumTotalTermFreq to be 6 and BM25's average length to be 3. Instead, sumTotalTermFreq and totalTermFreq sum the custom values (30), and BM25 explains dl=2.0 against avgdl=15.0. Each document's length is compared with an average inflated by the mean custom value, which quietly weakens length normalisation.

## Expected vs actual

- Expected: You expect sumTotalTermFreq to be 6 and BM25's average length to be 3.
- Actual: sumTotalTermFreq and totalTermFreq sum the custom values (30), and BM25 explains dl=2.0 against avgdl=15.0.

## Files

- [src/org/apache/lucene/search/TestTermDocFieldLengthVsStats.java](src/org/apache/lucene/search/TestTermDocFieldLengthVsStats.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestTermDocFieldLengthVsStats
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
