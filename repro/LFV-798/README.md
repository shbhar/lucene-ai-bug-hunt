# The document suggester checks its reserved end byte U+0000 only in the raw SuggestField value, not in the analyzed form, so an analyzer that emits U+0000 (for example HTMLStripCharFilter decoding '&amp;#0;') hides another document's suggestion or its own and adds FST inputs out of order at flush (with assertions enabled the IndexWriter closes)

Bug LFV-798 · Area: suggest / document (NRT) suggester (SuggestField's raw-value reserved-character check vs. the analyzed form that CompletionTokenStream passes to NRTSuggesterBuilder's FST encoding) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-798.html](../../bugs/LFV-798.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index suggestions with SuggestField through an analyzer that decodes HTML character references (HTMLStripCharFilter + KeywordTokenizer), for example 'a' (weight 1) and 'a&amp;#0;' (weight 2), and expect a PrefixCompletionQuery 'a' with n=100 to return both documents, as it does with '&amp;#1;'. Instead only the second document comes back; and with 'a&amp;#0;&amp;#0;', 'a', 'a', 'b' the commit fails with an 'inputs are added out of order' AssertionError that closes the IndexWriter when assertions are enabled, while without assertions 'a&amp;#0;&amp;#0;' is never found for its own value. SuggestField rejects U+0000, the byte that ends every entry in the suggester's FST, only in the raw value, so the analyzed form can still carry it and collide with that terminator.

SuggestField "a" (weight 1) and "a&amp;#0;" (weight 2), analyzed by htmlStrip + keyword, both accepted; the analyzed U+0000 collides with the suggester's END_BYTE and hides the plain suggestion.

## Expected vs actual

Expected PrefixCompletionQuery "a" to return docs [1, 0]; actual [1] on main 645d4fe14a2 with -ea and -da.

## Files

- [src/org/apache/lucene/search/suggest/document/TestSuggestFieldAnalyzedEndByte.java](src/org/apache/lucene/search/suggest/document/TestSuggestFieldAnalyzedEndByte.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.suggest.document.TestSuggestFieldAnalyzedEndByte
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
