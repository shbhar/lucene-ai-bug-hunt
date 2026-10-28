# GradientFormatter and SpanGradientFormatter reverse the colour gradient on every channel where the min colour is larger than the max colour, so with the javadoc's own white-to-black example the top-scoring term is drawn white

Bug LFV-887 · Area: highlighter / classic Highlighter formatters (GradientFormatter, SpanGradientFormatter) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build new GradientFormatter(maxScore, "#FFFFFF", "#000000", null, null), the javadoc's own example (white for a score of zero, black for maxScore and above), and highlight a term whose score is maxScore, expecting it to be drawn black. Instead it is drawn white (&lt;font color="#FFFFFF"&gt;) and weaker terms get darker colours, because getColorVal always runs from the numerically smaller channel value to the larger one, whatever order the min and max colours were given in. The same happens on every colour channel where min &gt; max, in GradientFormatter and in SpanGradientFormatter, which inherits the method.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It is drawn white (&lt;font color="#FFFFFF"&gt;) and weaker terms get darker colours, because getColorVal always runs from the numerically smaller channel value to the larger one, whatever order the min and max colours were given in.

## Files

- [src/org/apache/lucene/search/highlight/TestGradientFormatterDirection.java](src/org/apache/lucene/search/highlight/TestGradientFormatterDirection.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.highlight.TestGradientFormatterDirection
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
