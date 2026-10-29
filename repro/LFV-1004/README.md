# AnalyzingInfixSuggester, WFSTCompletionLookup and FreeTextSuggester allocate storage for num results before they know how many suggestions match, so lookup(key, false, Integer.MAX_VALUE) on an 8-entry suggester throws instead of returning the 8 suggestions, and a large num allocates memory in proportion to num

Bug LFV-1004 · Area: suggest / Lookup.lookup result sizing (AnalyzingInfixSuggester, WFSTCompletionLookup, FreeTextSuggester) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingInfixSuggester, WFSTCompletionLookup or FreeTextSuggester from 8 entries that start with 'luc' and call lookup("luc", false, Integer.MAX_VALUE) to get every suggestion; num is documented as the 'maximum number of results to return', so you expect the 8 suggestions that num = 1000 returns, which is what TSTLookup and FSTCompletionLookup give for the same call. Instead each of the three sizes its result storage by num before it knows how many entries match (AnalyzingInfixSuggester builds a TopFieldCollectorManager of num slots that, unlike IndexSearcher.search, it never caps at maxDoc; the other two call new ArrayList&lt;&gt;(num)), so the call throws OutOfMemoryError 'Requested array size exceeds VM limit' (on 9.12.3, 10.5.2 and the 10.6 RC, AnalyzingInfixSuggester throws IllegalArgumentException 'maxSize must be &gt;= 0 and &lt; 2147483631' instead). A smaller but still large num succeeds and allocates memory in proportion to num rather than to the matches: 114 MiB per lookup for AnalyzingInfixSuggester and 38 MiB for the other two at num = 10^7.

The test shows that `AnalyzingInfixSuggester`, `WFSTCompletionLookup` and `FreeTextSuggester` allocate storage for `num` results before they know how many entries match, so `lookup("luc", false, Integer.MAX_VALUE)` on a 1-entry suggester ('lucene') throws.

## Expected vs actual

Expected the 1 suggestion (num is documented as a maximum). Actual: `OutOfMemoryError: Requested array size exceeds VM limit` (main stops at the first, AnalyzingInfixSuggester.java:741; the other two fail the same way at WFSTCompletionLookup.java:183 and FreeTextSuggester.java:519).

## Files

- [src/org/apache/lucene/search/suggest/TestLookupMaxNum.java](src/org/apache/lucene/search/suggest/TestLookupMaxNum.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.TestLookupMaxNum
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
