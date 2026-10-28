# UnifiedHighlighter re-analysis under the default WEIGHT_MATCHES flag builds its MemoryIndex without payloads, so payload-checking queries get missing or extra highlights or make highlight throw IllegalArgumentException

Bug LFV-268 · Area: highlighter / UnifiedHighlighter re-analysis offset source (MemoryIndexOffsetStrategy under WEIGHT_MATCHES) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-268.html](../../bugs/LFV-268.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a field whose tokens carry payloads as a plain TextField (no offsets), search with SpanPayloadCheckQuery or with IntervalQuery(Intervals.term(term, payloadFilter)), which matches the document, and highlight the hit with the UnifiedHighlighter on its default flags. You expect the snippet the highlighter gives with offsets in postings or with WEIGHT_MATCHES off, 'both terms. only &lt;b&gt;terms&lt;/b&gt;.' for a check on the second 'terms'. Instead re-analysis builds its one-document MemoryIndex without payloads (MemoryIndexOffsetStrategy stores payloads only when the span PhraseHelper is position-sensitive, and WEIGHT_MATCHES replaces that helper with PhraseHelper.NONE), so Weight#matches on that copy finds nothing and the snippet is 'both terms. only terms.', while the interval query makes highlight throw IllegalArgumentException 'Cannot create a payload-filtered iterator over field body because it has no indexed payloads'.

## Expected vs actual

- Expected: Every test gets its baseline's snippet: `both terms. only <b>terms</b>.`, or `both <b>terms</b>. only terms.` for the exclusion.
- Actual: Three snippets have no highlight, the exclusion highlights both `terms`, and the interval test throws `IllegalArgumentException:... because it has no indexed payloads`.

## Files

- [src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterReanalysisPayloads.java](src/org/apache/lucene/search/uhighlight/TestUnifiedHighlighterReanalysisPayloads.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestUnifiedHighlighterReanalysisPayloads
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestUnifiedHighlighterPayloadQueries.java](TestUnifiedHighlighterPayloadQueries.java)
- [TestUnifiedHighlighterReanalysisPayloads.java](TestUnifiedHighlighterReanalysisPayloads.java)
- [TestPayloadHighlightDocumentedPaths.java](TestPayloadHighlightDocumentedPaths.java)
- [TestReanalysisPayloadQueries.java](TestReanalysisPayloadQueries.java)
- [TestSecurityReachReanalysisPayloads.java](TestSecurityReachReanalysisPayloads.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
