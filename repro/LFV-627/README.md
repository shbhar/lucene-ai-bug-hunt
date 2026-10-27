# NGramPhraseQuery matches documents its own PhraseQuery does not whenever adjacent gram positions do not come from adjacent text: multi-valued n-gram fields with a position increment gap below n (Lucene's default gap is 0), CJKAnalyzer text with a break between CJK runs (punctuation, a digit, a Latin word or a stop word), and NGramTokenizer pre-tokenizing through isTokenChar

Bug LFV-627 · Area: core / search (NGramPhraseQuery rewrite), on n-gram fields built with analysis-common (NGramTokenizer, CJKAnalyzer / CJKBigramFilter) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap the analyzer's phrase query in NGramPhraseQuery, which its javadoc presents as a PhraseQuery optimized for n-gram fields that you may use 'rather than PhraseQuery' (the introducing review said 'the same results will be returned, scoring will differ'), and expect the same matching documents. Instead the rewrite keeps only the first gram, every n-th gram and the last gram at their relative positions ("ab bc cd" becomes "ab ? cd"), which is only correct when grams n positions apart always come from one unbroken run of text, and nothing checks that. So a 2-gram field with the two values 'xab' and 'zcd' (default gap 0), CJKAnalyzer text such as '東京。新大学' or '東京6大学野球', and a whitespace-pre-tokenized 2-gram field holding 'xab zcd' or 'multi long' all match a phrase ('abcd', '東京大学', 'tion') that the PhraseQuery itself does not match.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The rewrite keeps only the first gram, every n-th gram and the last gram at their relative positions ("ab bc cd" becomes "ab ? cd"), which is only correct when grams n positions apart always come from one unbroken run of text, and nothing checks that.

## Files

- [src/org/apache/lucene/search/TestNGramPhraseQueryMatchesItsPhraseQuery.java](src/org/apache/lucene/search/TestNGramPhraseQueryMatchesItsPhraseQuery.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestNGramPhraseQueryMatchesItsPhraseQuery
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
