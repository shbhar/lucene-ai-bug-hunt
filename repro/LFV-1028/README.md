# StandardQueryParser's two javadoc code examples, the class's 'Typical usage, including configuration tweaks' and the StandardQueryParser(Analyzer) constructor's 'The same as:', have never compiled: they call setAllowLeadingWildcard and setAnalyzer on getQueryConfigHandler, which returns a QueryConfigHandler that has no such setters (they are methods of StandardQueryParser itself), and the class example also assigns that QueryConfigHandler to a StandardQueryConfigHandler variable (documentation defect; every release since 2.9.0)

Bug LFV-1028 · Area: queryparser / flexible StandardQueryParser (class and constructor javadoc examples) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You copy the StandardQueryParser class javadoc's 'Typical usage, including configuration tweaks' (StandardQueryConfigHandler config = qpHelper.getQueryConfigHandler; config.setAllowLeadingWildcard(true); config.setAnalyzer(new WhitespaceAnalyzer)) or the constructor javadoc's 'The same as:' (qp.getQueryConfigHandler.setAnalyzer(analyzer)) and expect it to compile. Instead javac rejects four lines: getQueryConfigHandler is declared to return QueryConfigHandler, which cannot be assigned to StandardQueryConfigHandler, and neither config handler has setAllowLeadingWildcard or setAnalyzer (they offer only the key-based get/has/set/unset(ConfigurationKey,...)). The setters are methods of StandardQueryParser itself, which is exactly what the constructor body calls (this.setAnalyzer(analyzer)), so the fix is javadoc only.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Javac rejects four lines: getQueryConfigHandler is declared to return QueryConfigHandler, which cannot be assigned to StandardQueryConfigHandler, and neither config handler has setAllowLeadingWildcard or setAnalyzer (they offer only the key-based get/has/set/unset(ConfigurationKey,...)).

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestStandardQueryParserJavadocExamples.java](src/org/apache/lucene/queryparser/flexible/standard/TestStandardQueryParserJavadocExamples.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestStandardQueryParserJavadocExamples
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
