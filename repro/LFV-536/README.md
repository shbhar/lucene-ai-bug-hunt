# ConcatenateGraphFilter, its factory and CompletionAnalyzer misdocument maxGraphExpansions: the javadoc promises a TooComplexToDeterminizeException when the token graph has more than maxGraphExpansions paths (and 'no limit on graph expansions' for CompletionAnalyzer's 1- and 3-argument constructors), but the code silently keeps the first maxGraphExpansions paths (default 10000) and also uses the same value as the determinize work limit, so it can throw for a graph with fewer paths than the limit (documentation defect; the truncation is intended)

Bug LFV-536 · Area: analysis-common / ConcatenateGraphFilter and ConcatenateGraphFilterFactory (maxGraphExpansions javadoc); also suggest / CompletionAnalyzer · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set maxGraphExpansions on ConcatenateGraphFilter (or its factory), or use CompletionAnalyzer's 1- or 3-argument constructor, and expect what the javadoc says: a TooComplexToDeterminizeException when the token graph has more than that many paths, and, for CompletionAnalyzer, no limit at all. Instead the filter silently emits only the first maxGraphExpansions paths (a 4-path synonym graph at limit 3 gives 3 tokens and no error; CompletionAnalyzer keeps 10000 of 16384 paths), and because the same number is also passed to determinize as its work limit, a 2-path graph at limit 3 throws. Silent truncation is the suggester's deliberate design (LUCENE-4971), so the fix is to correct the javadoc, not the code.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The filter silently emits only the first maxGraphExpansions paths (a 4-path synonym graph at limit 3 gives 3 tokens and no error; CompletionAnalyzer keeps 10000 of 16384 paths), and because the same number is also passed to determinize as its work limit, a 2-path graph at limit 3 throws.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestConcatenateGraphMaxGraphExpansions.java](src/org/apache/lucene/analysis/miscellaneous/TestConcatenateGraphMaxGraphExpansions.java) (JUnit test)
- [src/org/apache/lucene/search/suggest/document/TestCompletionAnalyzerDefaultGraphExpansions.java](src/org/apache/lucene/search/suggest/document/TestCompletionAnalyzerDefaultGraphExpansions.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestConcatenateGraphMaxGraphExpansions \
       --select-class org.apache.lucene.search.suggest.document.TestCompletionAnalyzerDefaultGraphExpansions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
