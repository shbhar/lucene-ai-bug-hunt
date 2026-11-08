# FastVectorHighlighter: its default SimpleBoundaryScanner can cut a fragment between the two halves of a supplementary character (an emoji, a CJK Extension B ideograph) when no boundary char lies within 20 chars of the cut, so the snippet starts with a lone low surrogate or ends with a lone high surrogate (every release with FastVectorHighlighter, 2.9 to 10.6.0)

Finding LFV-1396 · Area: highlighter / FastVectorHighlighter fragment edges (SimpleBoundaryScanner raw-offset fallback, BaseFragmentsBuilder.getFragmentSourceMSO) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You store the text 40 x U+1F600 (an emoji, two UTF-16 chars each) + ' foo' in a field with term vectors (positions and offsets), search f:foo and call new FastVectorHighlighter().getBestFragment(fieldQuery, reader, 0, "f", 100), expecting a snippet made of whole characters. Instead you get '\uDE00' + 23 x U+1F600 + ' &lt;b&gt;foo&lt;/b&gt;': the fragment is centred on the match in UTF-16 units, its start (offset 33) falls between the two halves of the 17th emoji, and SimpleBoundaryScanner, finding none of its boundary chars within 20 chars, keeps that raw offset, so the snippet starts with a lone low surrogate; the fragment end has the same fallback and can end in a lone high surrogate. The match itself is highlighted correctly; only the snippet edge is malformed.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get '\uDE00' + 23 x U+1F600 + ' &lt;b&gt;foo&lt;/b&gt;': the fragment is centred on the match in UTF-16 units, its start (offset 33) falls between the two halves of the 17th emoji, and SimpleBoundaryScanner, finding none of its boundary chars within 20 chars, keeps that raw offset, so the snippet starts with a lone low surrogate; the fragment end has the same fallback and can end in a lone high surrogate.

## Files

- [src/org/apache/lucene/search/vectorhighlight/TestFastVectorHighlighterSurrogatePair.java](src/org/apache/lucene/search/vectorhighlight/TestFastVectorHighlighterSurrogatePair.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.vectorhighlight.TestFastVectorHighlighterSurrogatePair
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
