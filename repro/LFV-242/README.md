# NRTSuggester returns too few or wrongly ranked suggestions in a segment with only a few deleted documents, because its deleted-document queue slack (long)(topN / liveDocsRatio) rounds back down to topN

Bug LFV-242 · Area: suggest / document (NRT) suggester (NRTSuggester.getMaxTopNSearcherQueueSize via SuggestIndexSearcher and CompletionQuery; Util.TopNSearcher queue depth) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-242.html](../../bugs/LFV-242.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index suggestions with SuggestField, delete one document or update it (which deletes the old version), and ask SuggestIndexSearcher.suggest for the top n completions; you expect the top n live completions, because NRTSuggester enlarges its bounded search queue 'to account for the filtered out suggestions' of deleted documents. With few deletions that enlargement, (long)(topN / liveDocsRatio), rounds back down to topN (for one deletion in ten documents, (long)(1 / 0.9) = 1), so a deleted completion that takes a result slot is thrown away and nothing replaces it: with abc_0, the best of abc_0..abc_9 in one segment, deleted, suggest('abc_', 1) returns instead of [abc_1], and after updateDocument lowers the best entry's weight from 1000 to 1, suggest(num=1) returns that entry at weight 1 instead of the live entry at weight 999. Deleting more documents (5 of 10) gives the queue enough room and the same lookup works.

The test shows that the document suggester returns too few suggestions from a segment with few deletions: NRTSuggester's deleted-document queue slack `(long)(topN / liveDocsRatio)` rounds back down to topN (here `(long)(1 / 0.9) = 1`), so a deleted completion takes the only queue slot and nothing replaces it.

## Expected vs actual

Expected for one segment abc_0..abc_9 (weights 100..91) with abc_0 deleted, `suggest(prefix "abc_", num=1)`: `[abc_1]`; actual: `expected:<[abc_1]> but was:<[]>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/suggest/document/TestNRTSuggesterOneDeletion.java](src/org/apache/lucene/search/suggest/document/TestNRTSuggesterOneDeletion.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.document.TestNRTSuggesterOneDeletion
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
