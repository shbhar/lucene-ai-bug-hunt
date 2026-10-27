# NRTSuggester returns too few or wrongly ranked suggestions in a segment with only a few deleted documents, because its deleted-document queue slack (long)(topN / liveDocsRatio) rounds back down to topN

Bug LFV-242 · Area: suggest / document (NRT) suggester (NRTSuggester.getMaxTopNSearcherQueueSize via SuggestIndexSearcher and CompletionQuery; Util.TopNSearcher queue depth) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-242.html](../../bugs/LFV-242.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index suggestions with SuggestField, delete one document or update it (which deletes the old version), and ask SuggestIndexSearcher.suggest for the top n completions; you expect the top n live completions, because NRTSuggester enlarges its bounded search queue 'to account for the filtered out suggestions' of deleted documents. With few deletions that enlargement, (long)(topN / liveDocsRatio), rounds back down to topN (for one deletion in ten documents, (long)(1 / 0.9) = 1), so a deleted completion that takes a result slot is thrown away and nothing replaces it: with abc_0, the best of abc_0..abc_9 in one segment, deleted, suggest('abc_', 1) returns instead of [abc_1], and after updateDocument lowers the best entry's weight from 1000 to 1, suggest(num=1) returns that entry at weight 1 instead of the live entry at weight 999. Deleting more documents (5 of 10) gives the queue enough room and the same lookup works.

## Expected vs actual

- Expected: `[abc_1/99]` (1 of 10 deleted), `[abc_1/9999]` (1 of 1,000), `[abc_1/99]` (after `updateDocument`) and `[abc_0/100, abc_2/98]` (`num=2`).
- Actual: ``, ``, `[abc_0/1]` and `[abc_0/100]`; the half-deleted control returns `[abc_1/99]` as expected.

## Files

- [src/org/apache/lucene/search/suggest/document/TestNRTSuggesterFewDeletions.java](src/org/apache/lucene/search/suggest/document/TestNRTSuggesterFewDeletions.java) (JUnit test)
- [src2/org/apache/lucene/search/suggest/document/TestNRTSuggesterFewDeletions.java](src2/org/apache/lucene/search/suggest/document/TestNRTSuggesterFewDeletions.java) (JUnit test)
- [src3/org/apache/lucene/search/suggest/document/TestNRTSuggesterFewDeletions.java](src3/org/apache/lucene/search/suggest/document/TestNRTSuggesterFewDeletions.java) (JUnit test)
- [src4/org/apache/lucene/search/suggest/document/TestNRTSuggesterFewDeletions.java](src4/org/apache/lucene/search/suggest/document/TestNRTSuggesterFewDeletions.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')
javac -d classes-src3 -cp "$CP" $(find src3 -name '*.java')
javac -d classes-src4 -cp "$CP" $(find src4 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestNRTSuggesterFewDeletions
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestNRTSuggesterFewDeletions
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src3:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestNRTSuggesterFewDeletions
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src4:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestNRTSuggesterFewDeletions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSuggestFewDeletions.java](TestSuggestFewDeletions.java)
- [TestSuggestFewDeletionsLimit.java](TestSuggestFewDeletionsLimit.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
