# Test framework: AssertingLeafCollector's DocIdStream wrapper ignores upTo in forEach(int upTo, IOIntConsumer) and passes every remaining doc to the consumer, so a collector that reads a stream in bounded steps under AssertingIndexSearcher (the default of LuceneTestCase.newSearcher) gets wrong per-step results with -da and a spurious 'exhausted' AssertionError on its next legal call with -ea

Bug LFV-664 · Area: test-framework / search: AssertingLeafCollector DocIdStream wrapper (AssertingIndexSearcher, bulk collection) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

In a Lucene test you search with LuceneTestCase.newSearcher (an AssertingIndexSearcher) and a collector whose collect(DocIdStream) first calls stream.forEach(5, consumer) and then stream.count for the rest, which the DocIdStream javadoc allows ('up to the given upTo exclusive... It is not possible to iterate these doc IDs again later on'). You expect the split a plain IndexSearcher gives: docs 0-4 in the first step and docs 5-9 counted afterwards. Instead the test framework's wrapper calls the wrapped stream's unbounded forEach, so the first step also receives docs 5-9 and the second step gets nothing (with -da), and with -ea the nested wrapper then rejects the legal count with AssertionError 'exhausted'.

## Expected vs actual

- Expected: You expect the split a plain IndexSearcher gives: docs 0-4 in the first step and docs 5-9 counted afterwards.
- Actual: The test framework's wrapper calls the wrapped stream's unbounded forEach, so the first step also receives docs 5-9 and the second step gets nothing (with -da), and with -ea the nested wrapper then rejects the legal count with AssertionError 'exhausted'.

## Files

- [src/org/apache/lucene/tests/search/TestAssertingDocIdStreamForEachUpTo.java](src/org/apache/lucene/tests/search/TestAssertingDocIdStreamForEachUpTo.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed 5EED0205
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=5EED0205 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.tests.search.TestAssertingDocIdStreamForEachUpTo
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
