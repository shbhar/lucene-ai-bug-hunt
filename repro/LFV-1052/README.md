# Util.TopNSearcher returns a queued final output without calling acceptResult, so FreeTextSuggester spends a top-N slot on a word it already predicted and drops a better suggestion

Bug LFV-1052 · Area: util / FST (Util.TopNSearcher.search, 'Empty string!' branch) and suggest / FreeTextSuggester (lookup's per-level seen-token filter) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a FreeTextSuggester (default bigram model) from text in which 'foo' occurs 9 times (once followed by 'bar'), 'barn' 8 times and 'bar' 10 times, and call lookup("foo bar", 1). With stupid backoff 'foo bar' scores 1/9 = 0.111 and 'barn' 0.4 x 8/27 = 0.119, and 'bar' does not count at the unigram level because the bigram model already predicted it, so you expect [barn], which is also what upstream TestFreeTextSuggester's own reference model gives. You get [foo bar]: 'bar' is itself a word, so Util.TopNSearcher returns it from its 'Empty string!' branch without calling the acceptResult filter that rejects already-predicted words; it takes the unigram level's only slot, is then thrown away as a duplicate, and 'barn' is never considered.

## Expected vs actual

- Expected: You expect [barn], which is also what upstream TestFreeTextSuggester's own reference model gives.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSuggesterSeenWord.java](src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSuggesterSeenWord.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestFreeTextSuggesterSeenWord
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
