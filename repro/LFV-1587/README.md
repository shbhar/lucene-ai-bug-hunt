# CJKBigramFilter with outputUnigrams=false (its default) keeps its run of CJK characters across a non-CJK token when the next CJK token starts where the last CJK character ended, and can then leave a CJK character out of every term with no error (ICUNormalizer2CharFilter &gt; StandardTokenizer &gt; CJKBigramFilter on 月㏠, where the char filter turns ㏠ into '1日': the terms are 月 and 1, no 日) or emit a bigram behind that token whose start offset goes backwards, so that IndexWriter rejects the document (日日㋀日); run with ICUNormalizer2CharFilter in front of StandardTokenizer and of ICUTokenizer and with a MappingCharFilter rule in front of StandardTokenizer; outputUnigrams=true and the stock CJKAnalyzer, which has no char filter, showed neither

Finding LFV-1587 · Area: analysis / CJK (CJKBigramFilter behind a char filter that turns one source character into a non-CJK token and a CJK token; a chain the application builds) · Priority: low · Impact: silent wrong results · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an analyzer with ICUNormalizer2CharFilter in front of StandardTokenizer and CJKBigramFilter and index the text 月㏠, in which the char filter turns the day symbol ㏠ (U+33E0) into '1日'. You expect the document to hold the term 日, as it does when you index the already normalized text 月1日 (terms 月, 1 and 日): the class javadoc says 'when a CJK character has no adjacent characters to form a bigram, it is output in unigram form'. Instead the document is indexed with no error and holds only the terms 月 and 1: the digit gets a zero-width offset, 日 starts where 月 ended, and the filter appends 日 to a run that the digit has already interrupted and then emits it neither in a bigram nor as a unigram; with two CJK characters in front (日日㋀日) it emits the bigram 日月 behind the digit with a start offset that goes backwards, and IndexWriter.addDocument throws IllegalArgumentException.

## Expected vs actual

- Expected: You expect the document to hold the term 日, as it does when you index the already normalized text 月1日 (terms 月, 1 and 日): the class javadoc says 'when a CJK character has no adjacent characters to form a bigram, it is output in unigram form'.
- Actual: The document is indexed with no error and holds only the terms 月 and 1: the digit gets a zero-width offset, 日 starts where 月 ended, and the filter appends 日 to a run that the digit has already interrupted and then emits it neither in a bigram nor as a unigram; with two CJK characters in front (日日㋀日) it emits the bigram 日月 behind the digit with a start offset that goes backwards, and IndexWriter.addDocument throws IllegalArgumentException.

## Files

- [src/org/apache/lucene/analysis/icu/TestCJKBigramFilterAfterNormalizerCharFilter.java](src/org/apache/lucene/analysis/icu/TestCJKBigramFilterAfterNormalizerCharFilter.java) (JUnit test)

## Run it

- Tested on Lucene main 3cee9ed259e9 (a commit of 2026-11-23): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 8e4ee3d41386 (a commit of 2026-11-23): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0), with ICU4J 77.1.
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (8e4ee3d41386) and on Lucene main, with and without assertions. The chain is one that the application builds: a char filter that turns one source character into a digit and a CJK character (here ICUNormalizer2CharFilter with its default normalizer), a tokenizer, and CJKBigramFilter with outputUnigrams=false, its default. The stock CJKAnalyzer, which has no char filter, and outputUnigrams=true showed neither outcome in the runs. Which characters: with the default normalizer a run over all 1,112,064 code points found 68 (the telegraph symbols for months, days and hours: U+32C0 to U+32CB, U+33E0 to U+33FE, U+3358 to U+3370), and in the runs the character had to stand directly behind a CJK character (with no CJK character in front of it, or with a space between the two, neither outcome showed). Known outside Lucene: the downstream bug report elastic/elasticsearch#50008 (open when read on 2026-11-23) reports the second outcome, the rejected document, with ICUTokenizer in the chain; the silent outcome, which this test shows, is not in that report. What the test does: it builds an Analyzer with ICUNormalizer2CharFilter in front of StandardTokenizer and CJKBigramFilter, indexes one document with the text 月㏠ (the char filter turns ㏠, U+33E0, into '1日') and expects docFreq of the term 日 to be 1, as for the already normalized text 月1日. The document is indexed with no error and docFreq is 0: java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;0&gt;. The second outcome, the rejected document (日日㋀日 behind the same chain: IndexWriter.addDocument throws IllegalArgumentException, 'offsets must not go backwards startOffset=1,endOffset=3,lastStartOffset=2'), is not what this test runs; two probe programs and a test showed it on branch_11x 262e019500ec, main b5a4546a5964 and 10.6.0. The block names every jar that the run needs: ICU4J is the third-party jar of the analysis-icu module (Maven Central, com.ibm.icu:icu4j; version 79.1 on main and on branch_11x, 77.1 for 10.6.0); without it the run reports 1 test failed with java.lang.NoClassDefFoundError: com/ibm/icu/text/Normalizer2$Mode, which is not the finding. The test is a LuceneTestCase: its input has no randomness, the test framework draws its environment from the seed, and the outcome was the same under each seed that was run. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for the release, set V=10.6.0 and ICU=77.1 and use the five 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-analysis-common, lucene-analysis-icu) in place of the five $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/analysis/icu: with the test copied to lucene/analysis/icu/src/test/org/apache/lucene/analysis/icu/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 8e4ee3d41386 and on main 3cee9ed259e9, with -Ptests.asserts=true and with -Ptests.asserts=false.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;0&gt; (docFreq of the term 日 is 0 and Lucene throws no exception; the probe runs list the terms of that document as 月 and 1); a run that fails with java.lang.NoClassDefFoundError: com/ibm/icu/text/Normalizer2$Mode lacks the ICU4J jar and does not show the finding.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT; ICU=79.1
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:icu4j-$ICU.jar   # ICU4J, the third-party jar of the analysis-icu module (Maven Central, com.ibm.icu:icu4j)
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# assertions on, seed 75AE75AE
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=75AE75AE \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.TestCJKBigramFilterAfterNormalizerCharFilter
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
