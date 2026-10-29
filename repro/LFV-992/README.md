# analysis-icu overview page: the ICUTokenizer example and the three collation examples (Farsi range, Danish sort, Turkish case) call APIs removed between Lucene 4.0 and 10.0, and on main the Serbian transliteration example is not valid Java (documentation defect)

Bug LFV-992 · Area: analysis-icu / module overview documentation (overview.html 'Example Usages') · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the analysis-icu module overview, which offers 'Example Usages' for ICU tokenization, collation and transliteration, and copy one into your code. The tokenizer example and the Farsi range, Danish sort and Turkish case collation examples do not compile on any current release, because they call APIs Lucene removed between 4.0 and 10.0 (new ICUTokenizer(reader), Field.Index.ANALYZED, new IndexSearcher(dir, true), QueryParser.setAnalyzeRangeTerms, the Filter-taking search methods, Sort.setSort, IndexSearcher.doc(int)), and on main the Serbian transliteration example has a bare, uncommented sentence in its code. Rewritten with the current API (and, for the Danish sort, doc values) the examples compile and do what the page says, so only the page is wrong.

The test shows that five "Example Usages" blocks on the analysis-icu overview page (`lucene/analysis/icu/src/java/overview.html`: ICUTokenizer, Farsi range, Danish sort, Turkish case, Serbian transliteration), copied verbatim and compiled with javax.tools, do not compile against current Lucene.

## Expected vs actual

Expected: every example compiles. Actual: 16 error lines, overview.html:75, 121, 124, 127, 135, 151, 152, 156, 158, 160, 162, 176, 179, 182 (removed APIs such as `Field.Index`, `IndexSearcher(Directory, boolean)`, `Sort.setSort`, `IndexSearcher.doc(int)`, `ICUTokenizer(Reader)`) and 339-340 (an uncommented sentence, main only).

## Files

- [src/org/apache/lucene/analysis/icu/TestIcuOverviewExamplesCompile.java](src/org/apache/lucene/analysis/icu/TestIcuOverviewExamplesCompile.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.TestIcuOverviewExamplesCompile
```

The module(s) analysis-icu, facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/](../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
