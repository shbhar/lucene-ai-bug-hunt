# SolrSynonymParser rejects a whole synonyms file because one line holds only spaces or tabs, although its javadoc says blank lines are comments (ParseException 'Invalid synonym rule at line N', every release since 3.4.0)

Bug LFV-548 · Area: analysis-common / synonym (SolrSynonymParser; SynonymGraphFilterFactory and SynonymFilterFactory, format solr) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a synonyms file such as 'couch, sofa', then a line holding three spaces or a tab, then 'big, large', and expect it to parse, because the SolrSynonymParser javadoc says 'Blank lines and lines starting with '#' are comments'. Instead the whole file is rejected with ParseException 'Invalid synonym rule at line 2': the parser skips a line only when it has zero length (or starts with '#'), so the whitespace-only line is parsed as a rule whose single term trims to the empty string, which the analyzer step rejects. A truly empty line parses fine, and so did whitespace-only lines in the Solr loader this format came from before 3.4.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The whole file is rejected with ParseException 'Invalid synonym rule at line 2': the parser skips a line only when it has zero length (or starts with '#'), so the whitespace-only line is parsed as a rule whose single term trims to the empty string, which the analyzer step rejects.

## Files

- [src/org/apache/lucene/analysis/synonym/TestSolrSynonymParserBlankLine.java](src/org/apache/lucene/analysis/synonym/TestSolrSynonymParserBlankLine.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.synonym.TestSolrSynonymParserBlankLine
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
