# FreeTextSuggester.lookup can offer the empty word as a next-word prediction when a zero-length token followed the key's last word in a text given to build(): AssertionError with assertions on; with assertions off a suggestion that ends in the separator and can take the only place of a one-entry list (an experimental class; every release from 4.6.0 to 10.6.0, branch_11x and main)

Finding LFV-1534 · Area: suggest / FreeTextSuggester (lookup: the completion search right behind a separator; the n-gram model that build() stores) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1534.html](../../bugs/LFV-1534.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a FreeTextSuggester (default bigram model) with an analyzer that turns some word into a token of length 0 and call lookup("foo ", 10) for a word that such a token followed: as run, the stock ArabicAnalyzer over the one text 'foo ـ bar' (a standalone tatweel, U+0640, between two words), or a whitespace tokenizer with a PatternReplaceFilter that blanks punctuation over 'foo - bar'. You expect predictions that are words of length 1 or more (here none, since nothing else followed foo): no javadoc sentence states that; an assertion in the class's own result loop says that the last word of a suggestion is not empty. Instead lookup throws AssertionError from that assertion when assertions are on, and with assertions off it returns the suggestion 'foo' plus the separator with nothing behind it; with a second text 'foo baz', lookup("foo ", 1) then returns only that suggestion and 'baz' is left out (a tie, 0.50 against 0.50). The same analyzers over 'foo bar' predict 'bar'.

## Expected vs actual

- Expected: No suggestion for `foo ` ends in the separator, that is, predicts a word of length 0 (for this one text: no suggestion at all).
- Actual: Assertions on: `java.lang.AssertionError` at `FreeTextSuggester.lookup(FreeTextSuggester.java:669)`. Assertions off: one suggestion, `foo` plus the separator (`a suggestion for "foo " predicts the empty word`).

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSuggesterZeroLengthToken.java](src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSuggesterZeroLengthToken.java) (JUnit test)

## Run it

- Tested on Lucene main 596ef11ddf96 (a commit of 2026-11-17): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 89e68e0f3a50 (a commit of 2026-11-17): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- What each build does: the test fails on Lucene main, on the 11.0 release branch (branch_11x) and with the 10.6.0 release jars, in one of two ways by the assertion setting. With assertions off, as the block below is printed, lookup returns the suggestion foo plus the separator and the test's own check fails: java.lang.AssertionError: a suggestion for "foo " predicts the empty word. With assertions on (-ea -esa in place of -da -dsa -Dtests.asserts=false) an assertion of the class itself fails inside lookup before the test's check is reached: java.lang.AssertionError without a message, thrown at FreeTextSuggester.java:669 on main and on the 11.0 branch and at :679 in 10.6.0. What the test does: it builds a FreeTextSuggester with default settings (a bigram model) over the stock ArabicAnalyzer from one text, the word foo, the code point U+0640 standing alone and the word bar (the analyzer turns the standalone U+0640 into a token of length 0), calls lookup("foo ", 10) and requires that no returned key ends in the separator. The test draws no random value; the seed still picks the test framework's codec, locale and time zone, and on main the test failed under ten different seeds (DEADBEEF, as printed, among them), each run in the way of its assertion setting. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put the five 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-analysis-common, lucene-suggest) in place of the five $LUCENE jars. With the jars of a release from 9.1.0 on the test compiles as shipped (run on 9.1.0 and 10.6.0); for an older release change the import to org.apache.lucene.util.LuceneTestCase, and from 4.6.0 to 4.9.1 also write new ArabicAnalyzer(TEST_VERSION_CURRENT), each with that release's test-framework dependencies (run this way on 9.0.0, 7.7.3 and 5.0.0 with assertions on and off, and on 4.10.4, 4.9.1 and 4.6.0 with assertions on: the 4.x test framework refuses a run without assertions). Two more changes on the old releases that were run: before 9.0.0 the analysis jar is named lucene-analyzers-common, and on 5.0.0 and the three 4.x releases the test-framework dependencies include JUnit 4.10, which the console launcher of the block below does not accept (it asks for JUnit 4.12 or later), so the runs there started org.junit.runner.JUnitCore with the class name instead. On 4.10.4 one other seed stopped in the test framework before the test ran ("fix your classpath to have tests-framework.jar before lucene-core.jar"); with the lucene-test-framework jar first on the class path that run failed as described here. The seed printed here did not need that on the three 4.x releases. The file at the top of this folder, which the finding page shows, is the same test with the licence header: its own frame in a stack trace is 16 lines further down. It is the form for the Gradle alternative at the end of this page, whose module is lucene/suggest.
- This finding was re-run on Lucene main 7b8418faa2ff on 2026-11-20: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, as printed (assertions off) with java.lang.AssertionError: a suggestion for "foo " predicts the empty word, and with assertions on with a java.lang.AssertionError without a message, thrown in FreeTextSuggester.lookup.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestFreeTextSuggesterZeroLengthToken
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestFreeTextSuggesterZeroLengthToken.java](TestFreeTextSuggesterZeroLengthToken.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
