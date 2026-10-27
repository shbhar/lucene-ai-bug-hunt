# SortedInputIterator leaves its temp input file (and, when reopening the sorted file fails, the sorted file too) in the temp Directory when its constructor fails, so every WFSTCompletionLookup or TSTLookup build that fails while reading or sorting its input leaks a temp file (regression since 6.0.0)

Bug LFV-790 · Area: suggest / SortedInputIterator temp-file cleanup (used by WFSTCompletionLookup and TSTLookup) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a WFSTCompletionLookup or a TSTLookup and the build fails while the input is being read or sorted, for example because the dictionary's next throws an IOException, or an entry has weight -1, which WFSTCompletionLookup documents it rejects; you expect the failed build to remove the temp files it created, as AnalyzingSuggester and FSTCompletionLookup do. Instead SortedInputIterator, which both suggesters use to sort their input, leaves its temp input file behind: its constructor copies and sorts the input in sort without any cleanup on failure and throws before the caller has an object it could close, so three failed builds leave suggest_input_0.tmp, suggest_input_1.tmp and suggest_input_2.tmp. This is a regression from LUCENE-7113 (6.0.0), which rewrote sort and dropped the failure cleanup that 5.5.x still had.

## Expected vs actual

- Expected: You expect the failed build to remove the temp files it created, as AnalyzingSuggester and FSTCompletionLookup do.
- Actual: SortedInputIterator, which both suggesters use to sort their input, leaves its temp input file behind: its constructor copies and sorts the input in sort without any cleanup on failure and throws before the caller has an object it could close, so three failed builds leave suggest_input_0.tmp, suggest_input_1.tmp and suggest_input_2.tmp.

## Files

- [src/org/apache/lucene/search/suggest/TestSortedInputIteratorFailedSortTempFiles.java](src/org/apache/lucene/search/suggest/TestSortedInputIteratorFailedSortTempFiles.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.TestSortedInputIteratorFailedSortTempFiles
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
