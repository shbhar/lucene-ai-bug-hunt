# TeeSinkTokenFilter: a sink never clears attributes that only its own filter chain adds (SinkTokenStream.incrementToken calls restoreState without clearAttributes), so a keyword marker or StemmerOverrideFilter before a stemmer on a sink silently leaves every token after the first protected word unstemmed

Bug LFV-651 · Area: analysis / TeeSinkTokenFilter (SinkTokenStream replay through AttributeSource.restoreState without clearAttributes: attributes that only the sink's chain adds) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-651.html](../../bugs/LFV-651.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one field from a TeeSinkTokenFilter and a second field from one of its sinks, wrapped in a keyword marker and a stemmer (for example SetKeywordMarkerFilter protecting 'quick', then PorterStemFilter), and expect the sink field to get what the same chain gives on the tokenizer: [the, quick, fox, jump] for 'The quick foxes jumped'. Instead it gets [the, quick, foxes, jumped]: the sink replays each token with restoreState but never calls clearAttributes, and the KeywordAttribute that only the sink's chain added is not in the tee's captured states, so once 'quick' is marked as a keyword every later token stays marked and is not stemmed. Nothing is thrown; a query for stemmed:fox finds nothing.

## Expected vs actual

- Expected: The sink field gets `[the, quick, fox, jump]` and `stemmed:fox` finds the document, as with the same filters on the tokenizer.
- Actual: The sink field gets `[the, quick, foxes, jumped]`, `stemmed:fox` finds 0 documents, and the framework's clearAttributes check fails at token 0.

## Files

- [src/org/apache/lucene/analysis/sinks/TestTeeSinkClearsSinkAttributes.java](src/org/apache/lucene/analysis/sinks/TestTeeSinkClearsSinkAttributes.java) (JUnit test)
- [src/TeeSinkSinkChainKeywordMain.java](src/TeeSinkSinkChainKeywordMain.java) (main() class)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.sinks.TestTeeSinkClearsSinkAttributes

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" TeeSinkSinkChainKeywordMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestTeeSinkSinkOnlyAttributes.java](TestTeeSinkSinkOnlyAttributes.java)
- [TestTeeSinkClearsSinkAttributes.java](TestTeeSinkClearsSinkAttributes.java)
- [TeeSinkSinkChainKeywordMain.java](TeeSinkSinkChainKeywordMain.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
