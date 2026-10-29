# The org.apache.lucene.analysis package overview (the TokenStream API tutorial) has seven code examples that do not compile on any release since 6.0: they call StandardAnalyzer(Version), WhitespaceTokenizer(Version) and FilteringTokenFilter(Version, TokenStream), removed in 5.0, and LengthFilter(boolean, TokenStream, int, int), gone since 4.4, subclass the final StandardAnalyzer, and define a custom AttributeImpl without reflectWith, abstract since 6.0 (documentation defect)

Bug LFV-989 · Area: core / analysis (org.apache.lucene.analysis package overview, package-info.java: the TokenStream API tutorial) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open the org.apache.lucene.analysis package overview, Lucene's tutorial for writing analyzers, token filters and attributes, and copy one of its examples, expecting it to compile as shown (the page even prints the output the code should produce). Seven examples do not compile on main or on any release since 6.0.0: they call StandardAnalyzer(Version), WhitespaceTokenizer(Version) and FilteringTokenFilter(Version, TokenStream), all removed in 5.0, and new LengthFilter(true, source, 3, Integer.MAX_VALUE), a signature gone since 4.4; one subclasses StandardAnalyzer, which is final; and the custom PartOfSpeechAttributeImpl lacks reflectWith(AttributeReflector), which has been abstract since 6.0. The APIs changed on purpose and the page was never updated: written against the current API (no Version argument, LengthFilter(source, 3, Integer.MAX_VALUE), a custom Analyzer or AnalyzerWrapper instead of a StandardAnalyzer subclass, and a reflectWith method) every example compiles.

The test shows that the code examples of the `org.apache.lucene.analysis` package overview (core `analysis/package-info.java`) do not compile on main: the test compiles the seven examples' stale lines, copied from the page (LUCENE_XY substituted), with the JDK compiler.

## Expected vs actual

Expected: no javac errors. Actual: 7 errors: no `StandardAnalyzer(Version)` (:183,:267), `cannot inherit from final StandardAnalyzer` (:267), no `WhitespaceTokenizer(Version)` (:617/:672/:876), no `LengthFilter(boolean, TokenStream, int, int)` (:673/:877), no `FilteringTokenFilter(Version, TokenStream)` (:709), and `PartOfSpeechAttributeImpl` does not implement abstract `reflectWith` (:809).

## Files

- [src/org/apache/lucene/analysis/TestAnalysisOverviewExamplesCompile.java](src/org/apache/lucene/analysis/TestAnalysisOverviewExamplesCompile.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestAnalysisOverviewExamplesCompile
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/](../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
