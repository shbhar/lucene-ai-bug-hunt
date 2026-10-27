# Document suggester with skipDuplicates=true and an index-time synonym returns fewer than n distinct suggestions (n=2 drops one that n=3 returns), because Util.TopNSearcher drops its queue before the last result although acceptPartialPath can still reject that path, and NRTSuggester sizes the queue from entries per analyzed form, which ignores synonym graph paths

Bug LFV-660 · Area: suggest / document suggester (NRTSuggester skipDuplicates, queue sizing) and util / FST (Util.TopNSearcher) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index suggestions through a CompletionAnalyzer with an index-time synonym (b =&gt; xb, keeping the original, so 'a b' is indexed as 'a b' and 'a xb'), add a second suggestion 'a xc', and call SuggestIndexSearcher.suggest(prefix 'a', 2, skipDuplicates=true); you expect the two distinct suggestions [a b/10, a xc/3], which n=3 does return. Instead you get only [a b/10]: Util.TopNSearcher sets its queue to null for the last result when maxQueueDepth == topN, assuming the last path always completes, but the duplicate filter (acceptPartialPath) rejects that path when it reaches 'a xb', so the sibling arc that leads to 'a xc' is never queued and the search ends early (called directly, TopNSearcher even reports isComplete=true for such a search). NRTSuggester makes the queue that small because it counts entries sharing one analyzed form, not the analyzed paths per suggestion that its javadoc describes, so a synonym never enlarges it; with two synonyms (b =&gt; {xb, yb}) the duplicate paths fill the queue and both n=2 and n=3 lose a suggestion even with the shortcut removed.

## Expected vs actual

- Expected: You expect the two distinct suggestions [a b/10, a xc/3], which n=3 does return.
- Actual: You get only [a b/10]: Util.TopNSearcher sets its queue to null for the last result when maxQueueDepth == topN, assuming the last path always completes, but the duplicate filter (acceptPartialPath) rejects that path when it reaches 'a xb', so the sibling arc that leads to 'a xc' is never queued and the search ends early (called directly, TopNSearcher even reports isComplete=true for such a search).

## Files

- [src/org/apache/lucene/search/suggest/document/TestNRTSuggesterDedupLastSlot.java](src/org/apache/lucene/search/suggest/document/TestNRTSuggesterDedupLastSlot.java) (JUnit test)
- [src/org/apache/lucene/util/fst/TestTopNSearcherLastPathPartialReject.java](src/org/apache/lucene/util/fst/TestTopNSearcherLastPathPartialReject.java) (JUnit test)
- [src2/org/apache/lucene/search/suggest/document/TestNRTSuggesterDedupLastSlot.java](src2/org/apache/lucene/search/suggest/document/TestNRTSuggesterDedupLastSlot.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestNRTSuggesterDedupLastSlot
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestNRTSuggesterDedupLastSlot

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.fst.TestTopNSearcherLastPathPartialReject
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
