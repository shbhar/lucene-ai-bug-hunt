# Classic Highlighter: SimpleFragmenter and SimpleSpanFragmenter move their fragment boundary only one fragment size per break, so after a long token or a long gap between tokens (a run of spaces, stripped markup) every following word becomes its own fragment and getBestFragment can return just the highlighted word (every release since 1.9)

Finding LFV-1367 · Area: highlighter / classic Highlighter fragmenting (SimpleFragmenter.isNewFragment and SimpleSpanFragmenter.isNewFragment fragment counter) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You highlight a text with the classic Highlighter and SimpleFragmenter, whose javadoc promises 'same-size fragments' of fragmentSize characters each, and the text has a long token or a long stretch without tokens (60 x's before 'alpha beta gamma ...' with SimpleFragmenter(20), or 300 spaces before a sentence with the default Highlighter and StandardAnalyzer). You expect the words after that stretch to be grouped into fragments of about fragmentSize characters again, so that the best fragment for 'gamma' is 'alpha beta &lt;B&gt;gamma&lt;/B&gt;', as it is without the long token. Instead isNewFragment moves its boundary only one fragment size per break, so the boundary stays behind the text, every following word starts its own fragment until it catches up, and getBestFragment returns just ' &lt;B&gt;gamma&lt;/B&gt;' (SimpleSpanFragmenter behaves the same).

## Expected vs actual

- Expected: You expect the words after that stretch to be grouped into fragments of about fragmentSize characters again, so that the best fragment for 'gamma' is 'alpha beta &lt;B&gt;gamma&lt;/B&gt;', as it is without the long token.
- Actual: isNewFragment moves its boundary only one fragment size per break, so the boundary stays behind the text, every following word starts its own fragment until it catches up, and getBestFragment returns just ' &lt;B&gt;gamma&lt;/B&gt;' (SimpleSpanFragmenter behaves the same).

## Files

- [src/org/apache/lucene/search/highlight/TestSimpleFragmenterOffsetGap.java](src/org/apache/lucene/search/highlight/TestSimpleFragmenterOffsetGap.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.highlight.TestSimpleFragmenterOffsetGap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/classic-highlighter-simple-fragmenter-no-catch-up-after-offset-gap/](../../models/classic-highlighter-simple-fragmenter-no-catch-up-after-offset-gap/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
