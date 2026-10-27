# A zero-length token makes TokenStreamToAutomaton drop every path through it, so ConcatenateGraphFilter emits nothing and the document suggester, AnalyzingSuggester and FuzzySuggester silently lose that suggestion (stock trigger: a standalone tatweel U+0640 with ArabicAnalyzer, PersianAnalyzer or SoraniAnalyzer)

Bug LFV-523 · Area: analysis / TokenStreamToAutomaton (core), reached through ConcatenateGraphFilter (analysis-common) and the completion suggesters (suggest: NRT document suggester, AnalyzingSuggester, FuzzySuggester) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-523.html](../../bugs/LFV-523.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the suggestion 'foo ـ bar' (a standalone Arabic tatweel between two words) with ArabicAnalyzer into a SuggestField, or build an AnalyzingSuggester or FuzzySuggester from it, and expect a lookup for 'foo' to return it next to 'foo bar', because ArabicAnalyzer turns the tatweel into an ordinary empty token and ConcatenateGraphFilter promises one output token for every path through the token graph. Instead TokenStreamToAutomaton adds a token's transitions only inside its per-byte loop, which never runs for a zero-length token, so the token's end state is unreachable: ConcatenateGraphFilter emits nothing, the document suggester indexes no entry for the value and the analyzing suggesters skip the input, so the suggestion never comes back. A stacked empty token loses only its own alternative path; nothing throws, with assertions on or off.

## Expected vs actual

- Expected: [foo ـ bar, foo bar] for the prefix "foo"; 1 path and 1 joined token for `a`, `""`, `b` (2 paths when the empty token is stacked).
- Actual: [foo bar] from `SuggestField` and `AnalyzingSuggester`; 0 paths (1 of 2 when stacked) and 0 tokens from `ConcatenateGraphFilter`; nothing throws.

## Files

- [src/org/apache/lucene/search/suggest/document/TestEmptyTokenDropsSuggestion.java](src/org/apache/lucene/search/suggest/document/TestEmptyTokenDropsSuggestion.java) (JUnit test)

## Run it

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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestEmptyTokenDropsSuggestion
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestEmptyTokenSuggestions.java](TestEmptyTokenSuggestions.java)
- [optionA2-hole-arc-unless-all-empty.diff](optionA2-hole-arc-unless-all-empty.diff)
- [test-graphtokenizers.diff](test-graphtokenizers.diff)
- [TestEmptyTokenDropsSuggestion.java](TestEmptyTokenDropsSuggestion.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
