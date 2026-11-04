# FastVectorHighlighter matchedFields: the merging constructor WeightedPhraseInfo(Collection) drops the term infos of the first merged match (while it keeps that match's boost and offsets), so WeightedFragListBuilder underweights a passage matched in several matched fields and can return the wrong best fragment (since 4.6.0)

Finding LFV-1380 · Area: highlighter / vectorhighlight FieldPhraseList matchedFields merge (FieldPhraseList(FieldPhraseList[]) and WeightedPhraseInfo(Collection)), weighed by WeightedFragListBuilder / WeightedFieldFragList · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You highlight document 0, 'running fast xx xx xx swim fast' (19 more documents hold 'running fast', so swim is rare), with FastVectorHighlighter.getBestFragments(..., matchedFields = {content, content.plain}, 20, 1, new WeightedFragListBuilder(), new ScoreOrderFragmentsBuilder(), ...), where content is stemmed and content.plain is its unstemmed copy, for content:run OR content.plain:"running fast" OR content.plain:"swim fast". You expect the passage that content:run and content.plain:"running fast" both match to be weighed with the term infos of both matches, as the merging constructor's comment ('merge the boosts and termInfos') and WeightedFieldFragList's sum-of-distinct-IDF-weights formula say, so the best fragment is '&lt;b&gt;running fast&lt;/b&gt; xx xx xx' (weight 9.89 against 6.02). Instead the merged match keeps the summed boost and both offsets but only the second match's term infos ('run' is lost), the passage weighs 5.38, and the best fragment returned is 'xx &lt;b&gt;swim fast&lt;/b&gt;', with no error.

## Expected vs actual

- Expected: The best fragment is `<b>running fast</b> xx xx xx` and the test passes.
- Actual: `expected:<[<b>running fast</b> xx xx xx]> but was:<[xx <b>swim slow</b>]>` at line 76, with assertions on and off.

## Files

- [src/org/apache/lucene/search/vectorhighlight/TestMatchedFieldsMergeTermInfos.java](src/org/apache/lucene/search/vectorhighlight/TestMatchedFieldsMergeTermInfos.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.vectorhighlight.TestMatchedFieldsMergeTermInfos
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
