# Document suggester: NRTSuggester computes topN = num * prefixPaths.size in int, so when the product passes Integer.MAX_VALUE it wraps and SuggestIndexSearcher.suggest can silently return no suggestions or a truncated list (10^6 contexts in a segment: num 2147 returns 2147 suggestions, num 2148 returns none)

Bug LFV-999 · Area: suggest / document (NRT) suggester: NRTSuggester.lookup topN = num x prefix paths (SuggestIndexSearcher.suggest with ContextQuery, RegexCompletionQuery or FuzzyCompletionQuery; Util.TopNSearcher) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-999.html](../../bugs/LFV-999.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index suggestions with ContextSuggestField, one context each, so that one segment holds 10^6 distinct contexts, and call SuggestIndexSearcher.suggest(query, num, false) with a ContextQuery over a PrefixCompletionQuery that matches every context (no contexts added, which ContextQuery documents as 'suggest across all contexts', or a shared context prefix via addContext(prefix, boost, false)); you expect the top num completions, and num = 2147 does return 2147. With num = 2148 you get no suggestions and no error: NRTSuggester.lookup computes topN = num * prefixPaths.size in int (NRTSuggester.java:142), 2148 x 10^6 wraps to -2146967296, and Util.TopNSearcher's loop 'while (results.size &lt; topN)' never runs. When the product wraps to a small positive value the list is silently cut to that many (3000 contexts and num = 1431656 return 704 of 3000), and a RegexCompletionQuery or FuzzyCompletionQuery with many matching prefixes behaves the same (1000 regex prefixes and num = 2147484 return none).

The test shows that a ContextQuery over all contexts on one segment with 10^6 distinct contexts returns nothing for num 2148, because NRTSuggester.lookup's int topN = 2148 x 10^6 wraps negative (TestContextQueryManyContextsTopN#testAllContextsQueryOnOneMillionContexts, about 15-18 s).

## Expected vs actual

Expected 2148 suggestions (num is below maxDoc and below the 5000 queue cap); actual 0, with -ea and -da.

## Files

- [src/org/apache/lucene/search/suggest/document/TestContextQueryManyContextsTopN.java](src/org/apache/lucene/search/suggest/document/TestContextQueryManyContextsTopN.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 5EED0999
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0999 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestContextQueryManyContextsTopN
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/suggester-lookup-num-overflow-and-presize/](../../models/suggester-lookup-num-overflow-and-presize/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
