# Classic Highlighter's SimpleSpanFragmenter splits a phrase match across fragments when two phrases overlap, when a longer phrase shares a shorter one's start (the result then depends on clause order), or when the phrase starts on stacked synonyms: it protects only the first span that starts at the current position, skips the span lookup while it waits for a span to end, and never sees a span that starts on a token the Highlighter does not show it

Bug LFV-858 · Area: highlighter / classic Highlighter fragmenting (SimpleSpanFragmenter.isNewFragment; Highlighter.getBestTextFragments) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You highlight a text with the classic Highlighter, a QueryScorer and SimpleSpanFragmenter, whose javadoc says it 'breaks text up into same-size fragments but does not split up Spans', for a query with two overlapping phrases ('new york' OR 'york city hall'), two phrases that share a start ('new york' OR 'new york city hall'), or a phrase whose words get synonyms stacked on them by SynonymGraphFilter, and you expect every phrase match shorter than the fragment size to stay inside one fragment. Instead the fragmenter cuts inside the longer phrase ('The museum is near New York City' | ' Hall and the park', 'We flew to New York City' | ' Hall last week', 'I left my big laptop' | ' bag on the train'), so getBestFragment can return only part of the phrase, and in the same-start case listing the longer phrase first in the BooleanQuery makes the phrase come out whole. Hits, scores and which words are highlighted are correct; only the fragment boundaries are wrong.

## Expected vs actual

- Expected: You expect every phrase match shorter than the fragment size to stay inside one fragment.
- Actual: The fragmenter cuts inside the longer phrase ('The museum is near New York City' | ' Hall and the park', 'We flew to New York City' | ' Hall last week', 'I left my big laptop' | ' bag on the train'), so getBestFragment can return only part of the phrase, and in the same-start case listing the longer phrase first in the BooleanQuery makes the phrase come out whole.

## Files

- [src/org/apache/lucene/search/highlight/TestSimpleSpanFragmenterSpanTracking.java](src/org/apache/lucene/search/highlight/TestSimpleSpanFragmenterSpanTracking.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.highlight.TestSimpleSpanFragmenterSpanTracking
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
