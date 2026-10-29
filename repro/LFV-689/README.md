# FuzzyCompletionQuery with unicodeAware=true compares the query's code points with the UTF-8 bytes of the matched suggestion, so its prefix-similarity boost stops at the first non-ASCII letter and exact prefixes lose their boost advantage over typos

Bug LFV-689 · Area: suggest / document (NRT) suggester (FuzzyCompletionQuery: FuzzyCompletionWeight.setNextMatch prefix-similarity boost in unicodeAware mode, also inside ContextQuery) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-689.html](../../bugs/LFV-689.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a FuzzyCompletionQuery with unicodeAware=true, the documented option that measures the query 'in Unicode code points (actual letters) instead of bytes', and expect a suggestion that shares more leading letters with the typed prefix to get a larger boost, exactly as for ASCII text. Instead FuzzyCompletionWeight.setNextMatch compares the query's code points with the UTF-8 byte labels of the matched suggestion, so the count stops at the first non-ASCII letter: for the query 'aéc' over aécd (weight 3) and the typo axcd (weight 4) both get boost 1 and the exact prefix ranks second, [axcd/4.0, aécd/3.0], while the same data in ASCII ('abc' over abcd and axcd) gives [abcd/6.0, axcd/4.0]; for an all-CJK or all-Cyrillic query every suggestion gets boost 0, so the exact prefix only ties with typos and the weight alone decides. Only scores and ranking are wrong (the suggestions found are right), for the plain query and inside a ContextQuery.

The test shows that `FuzzyCompletionQuery` with `unicodeAware=true` compares the query's code points with the matched suggestion's UTF-8 bytes, so its prefix boost stops at the first non-ASCII letter and an exact prefix ranks below a typo.

## Expected vs actual

Expected for `aéc` over `aécd`/3 and the typo `axcd`/4 the same result as in ASCII, `[aécd/6.0, axcd/4.0]`; actual `[axcd/4.0, aécd/3.0]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/suggest/document/TestFuzzyCompletionQueryUnicodeAwareBoost.java](src/org/apache/lucene/search/suggest/document/TestFuzzyCompletionQueryUnicodeAwareBoost.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.document.TestFuzzyCompletionQueryUnicodeAwareBoost
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
