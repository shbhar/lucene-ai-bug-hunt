# AnalyzingInfixSuggester and BlendedInfixSuggester can return no suggestion for a key whose last word the query analyzer turns into a zero-length token (run: a standalone tatweel under ArabicAnalyzer): with minPrefixChars of 1 or more (default 4) and allTermsRequired = true (the default) the empty last token is looked up as a term of the edge n-gram field, whose shortest gram has one character (4.4.0 to 10.6.0, branch_11x and main; BlendedInfixSuggester from 4.7.0, its first release)

Finding LFV-1533 · Area: suggest / AnalyzingInfixSuggester and BlendedInfixSuggester lookup (getLastTokenQuery, the edge n-gram field) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1533.html](../../bugs/LFV-1533.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingInfixSuggester with its default settings over the stock ArabicAnalyzer, add the suggestion 'foo ـ bar' (a standalone tatweel, U+0640, between two words) and call lookup(key, 10, true, false) with the key 'foo ـ' (foo, a space, U+0640), expecting that suggestion because the key is its beginning. Instead you get an empty list and no error: the analyzer turns the tatweel into a zero-length token, and getLastTokenQuery sends a last token shorter than minPrefixChars to the edge n-gram field as an exact term, but the shortest gram of that field has one character, so the required clause matches nothing. The key 'foo' alone and the key 'foo ـ' followed by a space both return the suggestion, and so does the same suggester built with minPrefixChars = 0; BlendedInfixSuggester inherits the lookup and loses the suggestion the same way.

## Expected vs actual

- Expected: `lookup(key, 10, true, false)` with the key `foo ـ` returns 1 suggestion, `foo ـ bar`, the only one indexed: the key is its beginning.
- Actual: It returns an empty list: `java.lang.AssertionError: expected:<1> but was:<0>`.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestInfixSuggesterZeroLengthLastToken.java](src/org/apache/lucene/search/suggest/analyzing/TestInfixSuggesterZeroLengthLastToken.java) (JUnit test)

## Run it

- Tested on Lucene main 596ef11ddf96 (a commit of 2026-11-17): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 89e68e0f3a50 (a commit of 2026-11-17): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- What each build does: the test fails the same way on Lucene main, on the 11.0 release branch (branch_11x) and with the 10.6.0 release jars, with and without assertions. What the test does: it builds an AnalyzingInfixSuggester with the two-argument constructor (the default settings) over the stock ArabicAnalyzer, adds one suggestion (foo, a space, U+0640, a space, bar), calls refresh() and looks up the key that is the suggestion up to and including U+0640 with lookup(key, 10, true, false); it expects 1 suggestion and fails with java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;0&gt;. The test draws no random value of its own and failed the same way with every seed that was run. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put the five 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-analysis-common, lucene-suggest) in place of the five $LUCENE jars. The file compiles unchanged against these three builds and against the jars of releases back to 9.1.0 (run on 10.5.2, 9.12.3 and 9.1.0: the same failure). With the jars of a release from 5.0.0 to 9.0.0 it needs one change, the import org.apache.lucene.util.LuceneTestCase, and that release's own test-framework dependencies (run on 9.0.0, 8.11.4, 7.7.3, 6.6.6 and 5.0.0: the same failure; the last two with JUnit's own runner in place of the console launcher). The 4.x releases need a rewritten test (other constructor arguments, build() in place of add() and refresh()). The file at the top of this folder, which the finding page shows, is the same file; it is the form for the Gradle alternative at the end of this page, whose module is lucene/suggest.
- This finding was re-run on Lucene main b5a4546a5964 on 2026-11-22: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;0&gt;.
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
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestInfixSuggesterZeroLengthLastToken
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestInfixSuggesterZeroLengthLastToken.java](TestInfixSuggesterZeroLengthLastToken.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
