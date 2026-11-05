# FuzzyCompletionQuery accepts maxEdits outside 0..2 without the IllegalArgumentException that FuzzySuggester and FuzzyQuery throw, so SuggestIndexSearcher.suggest later fails with NullPointerException (maxEdits &gt;= 3) or ArrayIndexOutOfBoundsException (maxEdits &lt; 0), and only for keys long enough to be fuzzed

Finding LFV-1342 · Area: suggest / document (NRT) suggester (FuzzyCompletionQuery: 9-argument constructor and toLevenshteinAutomata; LevenshteinAutomata.toAutomaton) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1342.html](../../bugs/LFV-1342.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a FuzzyCompletionQuery for the document suggester with the 9-argument constructor and maxEdits = 3, hoping for more fuzziness, and run it with SuggestIndexSearcher.suggest. You expect either an IllegalArgumentException at construction, which FuzzySuggester, FuzzyQuery and FuzzyAutomatonBuilder throw for an edit distance outside 0..2, or a normal search. Instead the constructor keeps the value, LevenshteinAutomata.toAutomaton quietly returns null for it, and suggest() throws a NullPointerException from deep inside FSTUtil.intersectPrefixPaths (maxEdits = -1 gives an ArrayIndexOutOfBoundsException instead); keys shorter than minFuzzyLength (default 3) skip the fuzzy automaton and keep working, so short test inputs hide it.

## Expected vs actual

- Expected: The constructor throws `IllegalArgumentException` (or `suggest` returns "lucene"); the test passes.
- Actual: `suggest` throws `NullPointerException: ... because "a" is null` at `FSTUtil.java:53` (`:56` with assertions off).

## Files

- [src/org/apache/lucene/search/suggest/document/TestFuzzyCompletionQueryMaxEdits.java](src/org/apache/lucene/search/suggest/document/TestFuzzyCompletionQueryMaxEdits.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestFuzzyCompletionQueryMaxEdits
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFuzzyCompletionQueryMaxEdits.java](TestFuzzyCompletionQueryMaxEdits.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
