# Monitor.match(Document) does not document that a batch is matched as one index, so queries whose rewrite or statistics depend on the index (top-N multi-term rewrites incl. the default FuzzyQuery, CommonTermsQuery, MoreLikeThisQuery) can match a document differently in a batch than alone

Bug LFV-914 · Area: monitor / batch matching: Monitor.match(Document) and DocumentBatch (documentation) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register queries with a Monitor and match documents in batches with Monitor.match(Document), which the package javadoc offers as an alternative to matching them one at a time with Monitor.match(Document) and whose only documented batch caveat is that filter-field values must agree. Each registered query is in fact rewritten and run once over one small index that holds the whole batch, so a query whose rewrite or statistics depend on the index can give a document a different answer than it gets alone: a default FuzzyQuery('abcdef') matches 'abcdxy' alone but not in a batch with 50 documents that each hold a closer term, and a CommonTermsQuery that matches 'ab' alone matches neither copy in a batch of two copies of 'ab'. This is the designed behaviour (a plain IndexSearcher over an index of the same documents returns the same result), but neither Monitor.match(Document) nor the package documentation says that a batch is matched as one index, so users cannot tell that a document's matches can depend on its batch.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/monitor/TestMonitorBatchTopTermsRewrite.java](src/org/apache/lucene/monitor/TestMonitorBatchTopTermsRewrite.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestMonitorBatchTopTermsRewrite
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
