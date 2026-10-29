# AnalyzingSuggester and FuzzySuggester compute the top-N queue depth num * maxAnalyzedPathsForOneInput in int, so with a synonym (token graph) analyzer a large num wraps it: lookup silently returns too few suggestions and skips higher-weighted ones, or throws NoSuchElementException, and with assertions on lookup(key, false, Integer.MAX_VALUE) typically fails 'assert completions.isComplete'

Bug LFV-1003 · Area: suggest / analyzing: AnalyzingSuggester and FuzzySuggester lookup (FST top-N queue depth) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingSuggester (or FuzzySuggester) whose index-time analyzer expands one entry into several analyzed forms, for example a SynonymGraphFilter that also indexes 'lucene' as 'search' and 'find', and call lookup("luc", false, num) with a large num; num is documented as the 'maximum number of results to return', so you expect the 8 suggestions that num = 1000 returns. Instead the search's queue depth num * maxAnalyzedPathsForOneInput is computed in int (AnalyzingSuggester.java:786) and wraps: with 3 paths and num = 1431655766 it becomes 2, the top-N search prunes paths it still needs, and lookup returns [lucas, lucifer, lucid] with no error, skipping the higher-weighted luck, lucent and lucerne (FuzzySuggester: 5 of 8), and with 4 paths and num = 2^30 it becomes 0 and lookup throws NoSuchElementException. With assertions enabled, lookup(key, false, Integer.MAX_VALUE) on a suggester with 2 or more paths typically fails 'assert completions.isComplete' (:824), although the same call without assertions returns the right list.

The test shows that AnalyzingSuggester.lookup with num = 1431655766 and an entry that has 3 analyzed paths (synonyms) silently returns too few suggestions, because the queue depth num * 3 overflows int to 2.

## Expected vs actual

Expected: all 3 matches [ad, ac, ab]. Actual with -da: [ad, ac]. With -ea the class's own `assert completions.isComplete` (AnalyzingSuggester.java:824) fails first.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterLargeNum.java](src/org/apache/lucene/search/suggest/analyzing/TestAnalyzingSuggesterLargeNum.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestAnalyzingSuggesterLargeNum
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/suggester-lookup-num-overflow-and-presize/](../../models/suggester-lookup-num-overflow-and-presize/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
