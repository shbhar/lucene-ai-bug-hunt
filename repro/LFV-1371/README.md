# QueryBuilder.analyzeGraphBoolean and analyzeGraphPhrase reset the source TokenStream and then hand it to GraphTokenStreamFiniteStrings, which resets it again, so a QueryBuilder subclass that passes a fresh analyzer stream gets IllegalStateException 'TokenStream contract violation ... reset() called multiple times' instead of the graph query (every release since 6.4.0)

Finding LFV-1371 · Area: core / util QueryBuilder graph hooks (analyzeGraphBoolean, analyzeGraphPhrase) with util/graph GraphTokenStreamFiniteStrings · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1371.html](../../bugs/LFV-1371.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a QueryBuilder subclass and pass a fresh TokenStream from Analyzer.tokenStream whose analysis is a token graph (in the repro 'guinea pig', where a synonym 'cavy' spans both words) to the protected hook analyzeGraphBoolean or analyzeGraphPhrase. You expect the graph query, the same ((+field:guinea +field:pig) field:cavy) that createBooleanQuery builds for that text through the same hook. Instead the call throws IllegalStateException 'TokenStream contract violation: reset()/close() call missing, reset() called multiple times, ...': the hook calls source.reset() and then new GraphTokenStreamFiniteStrings(source), whose constructor resets the stream again, and a Tokenizer's second reset() leaves it a reader that throws on the first read.

## Expected vs actual

- Expected: `analyzeGraphBoolean` returns `((+field:guinea +field:pig) field:cavy)`, the query `createBooleanQuery` builds, and the test passes.
- Actual: `IllegalStateException: TokenStream contract violation: ... reset() called multiple times ...` at `QueryBuilder.analyzeGraphBoolean(QueryBuilder.java:507)`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/TestQueryBuilderGraphHookFreshStream.java](src/org/apache/lucene/util/TestQueryBuilderGraphHookFreshStream.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.TestQueryBuilderGraphHookFreshStream
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestQueryBuilderGraphHookFreshStream.java](TestQueryBuilderGraphHookFreshStream.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
