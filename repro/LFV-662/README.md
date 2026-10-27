# FreeTextSuggester.lookup throws AssertionError (-ea) or NullPointerException (-da) when a word the analyzer removed (a stop word) comes right before the key's last word, because it reads the count of the filler-only context '_', which the n-gram model never stores (lookup('the w') with the default constructor and EnglishAnalyzer)

Bug LFV-662 · Area: suggest / FreeTextSuggester (n-gram model built with ShingleFilter; lookup context counts after analysis holes) · Priority: medium · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a FreeTextSuggester with the default constructor and the stock EnglishAnalyzer over 'the wizard of oz' and call lookup('the w'), expecting the suggestion 'wizard': lookup documents only IllegalArgumentException and IllegalStateException, and the upstream tests testEndingHole and testTwoEndingHoles assert results for a stop-word analyzer. Instead lookup throws AssertionError at FreeTextSuggester.java:576 with -ea, or NullPointerException in decodeWeight (:724) with -da: the removed 'the' leaves a hole, so the bigram-level query token is '_ w', which matches the stored shingle '_ wizard', and lookup then reads the count of its context '_', which ShingleFilter never emits and the model therefore never stores. The same happens on the upstream testEndingHole model for lookup('wizard of o'), one keystroke after the query that test asserts.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Lookup throws AssertionError at FreeTextSuggester.java:576 with -ea, or NullPointerException in decodeWeight (:724) with -da: the removed 'the' leaves a hole, so the bigram-level query token is '_ w', which matches the stored shingle '_ wizard', and lookup then reads the count of its context '_', which ShingleFilter never emits and the model therefore never stores.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSuggesterStopWordContext.java](src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSuggesterStopWordContext.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestFreeTextSuggesterStopWordContext
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
