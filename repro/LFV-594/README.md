# QueryAutoStopWordAnalyzer inherits its delegate's reuse strategy although its analysis chain differs per field, so with a stock delegate (GLOBAL_REUSE_STRATEGY) the chain built for the first field a thread analyses is reused for every other field: one field's stop words drop another field's query terms and a field's own stop words are kept (since 4.5.0)

Bug LFV-594 · Area: analysis / QueryAutoStopWordAnalyzer (analysis-common query package; AnalyzerWrapper reuse strategy, Analyzer.ReuseStrategy) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a stock analyzer such as StandardAnalyzer in QueryAutoStopWordAnalyzer, which computes a separate set of very common words (stop words) for each field from the index, and use that one analyzer for queries on several fields. You expect each field to lose only its own stop words, as the class's per-field design and its upstream test testNoFieldNamePollution say ('Filter should not prevent stopwords in one field being used in another'). Instead the analyzer reuses the analysis chain it built for the first field it analysed on the thread, so a word that is common in one field is removed from queries on another field (MultiFieldQueryParser parses 'boring' to an empty query instead of variedField:boring), and in the other order a field's own stop words stay in.

## Expected vs actual

- Expected: You expect each field to lose only its own stop words, as the class's per-field design and its upstream test testNoFieldNamePollution say ('Filter should not prevent stopwords in one field being used in another').
- Actual: The analyzer reuses the analysis chain it built for the first field it analysed on the thread, so a word that is common in one field is removed from queries on another field (MultiFieldQueryParser parses 'boring' to an empty query instead of variedField:boring), and in the other order a field's own stop words stay in.

## Files

- [src/org/apache/lucene/analysis/query/TestQueryAutoStopWordReuse.java](src/org/apache/lucene/analysis/query/TestQueryAutoStopWordReuse.java) (JUnit test)
- [src/qaswa/QaswaReuseMain.java](src/qaswa/QaswaReuseMain.java) (main() class)
- [src/qaswa/QaswaReuseMain4x.java](src/qaswa/QaswaReuseMain4x.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
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
     execute --select-class org.apache.lucene.analysis.query.TestQueryAutoStopWordReuse

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" qaswa.QaswaReuseMain
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" qaswa.QaswaReuseMain4x
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
