# JoinUtil's single-valued term and numeric joins treat a 'from' document without a join value as if it had the empty term or the number 0, so they also return the 'to' document whose key is empty or 0 and add the value-less documents' scores to it (since 4.0.0)

Bug LFV-766 · Area: join / JoinUtil query-time join (single-valued term and numeric collectors) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You join orders to customers with JoinUtil.createJoinQuery("customer", false, "id", fromQuery, searcher, scoreMode), or the numeric overload with Integer.class, where fromQuery matches two orders and only one of them has a customer key, and expect only that order's customer back, because the javadoc returns the to-documents that 'have the same terms' as the matching from-documents. Instead the order without a key is collected as if its key were the empty term (term join) or 0 (numeric join), so the customer whose id is "" or 0 is returned too, in every ScoreMode, and a real customer 0 also gets the key-less order's score (Max 5.0 instead of 1.0). The same call with multipleValuesPerDocument=true on the same single-valued field, and the global-ordinals join, return the correct answer.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The order without a key is collected as if its key were the empty term (term join) or 0 (numeric join), so the customer whose id is "" or 0 is returned too, in every ScoreMode, and a real customer 0 also gets the key-less order's score (Max 5.0 instead of 1.0).

## Files

- [src/org/apache/lucene/search/join/TestJoinUtilMissingFromValue.java](src/org/apache/lucene/search/join/TestJoinUtilMissingFromValue.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestJoinUtilMissingFromValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
