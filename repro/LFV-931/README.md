# Eight analysis-factory class javadoc schema examples cannot be used as written: six are not well-formed XML (StopFilterFactory, JapaneseIterationMarkCharFilterFactory, BeiderMorseFilterFactory, OpenNLPTokenizerFactory, OpenNLPLemmatizerFilterFactory, DropIfFlaggedFilterFactory) and two name a class that is not a factory (LimitTokenOffsetFilterFactory's solr.LimitTokenOffsetFilter, PatternTypingFilterFactory's com.example.PatternTypingFilter) (documentation defect)

Bug LFV-931 · Area: analysis-common, analysis-kuromoji, analysis-phonetic, analysis-opennlp / analysis factories (class javadoc <fieldType> schema examples) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You copy the &lt;fieldType&gt; example from the class javadoc of one of eight analysis factories (StopFilterFactory, for example) into a schema and expect well-formed XML whose elements name registered factories, as the other 325 example elements in Lucene's factory javadocs do. Instead six snippets are not well-formed XML (a class attribute with no closing quote, start tags with no '&gt;' or '/&gt;', a stray '/&gt;' line, and '&lt;-- other filters --&gt;' instead of an XML comment), and two name a class that is not a factory: LimitTokenOffsetFilterFactory's example says solr.LimitTokenOffsetFilter (the filter, not its factory) and PatternTypingFilterFactory's own element says com.example.PatternTypingFilter. The PatternTyping example also lists solr.TokenAnalyzerFilter, a companion filter from a Solr patch that was never committed (a stale reference, not a typo), and its first javadoc sentence describes that companion filter rather than PatternTypingFilter.

The test shows that the `StopFilterFactory` class-javadoc schema example (copied verbatim) is not well-formed XML; it is one of the eight broken factory examples in this row (documentation defect).

## Expected vs actual

Expected: the example parses as XML. Actual: `expected well-formed XML, but: Element type "filter" must be followed by either attribute specifications, ">" or "/>".` (the `<filter>` tag is never closed; same with -ea and -da).

## Files

- [src/org/apache/lucene/analysis/core/TestStopFilterFactoryJavadocExample.java](src/org/apache/lucene/analysis/core/TestStopFilterFactoryJavadocExample.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.core.TestStopFilterFactoryJavadocExample
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/analysis-factory-javadoc-examples-and-resource-files-build-as-documented/](../../models/analysis-factory-javadoc-examples-and-resource-files-build-as-documented/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
