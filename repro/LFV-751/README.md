# BinaryPoint.newExactQuery and the one-dimensional newRangeQuery keep the caller's byte as the query bounds (BinaryPoint.pack returns the caller's array for one dimension), while the same factory packs a private copy for two or more dimensions, so a caller that refills its buffer silently changes queries it already built

Bug LFV-751 · Area: document / point query factories (BinaryPoint, PointRangeQuery) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-751.html](../../bugs/LFV-751.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a one-dimensional binary key into a scratch byte, build BinaryPoint.newExactQuery(field, scratch), write the next key into the same array, build a second query and OR the two; you expect each query to match the key it was built for, as BinaryPoint's two-dimension range queries and its one-value newSetQuery do. Instead both queries now mean the second key (they compare equal and print the same bounds) and the OR finds 1 document instead of 2; likewise a one-dimensional newRangeQuery whose lo/hi arrays the caller overwrites afterwards matches 0 documents instead of the range it was built for. BinaryPoint.pack returns new BytesRef(point[0]), a wrapper over the caller's array, when there is one dimension, and PointRangeQuery stores that array as its bounds, so matching, equals, hashCode and toString read whatever the caller's array holds at the time.

## Expected vs actual

- Expected: The OR of three exact queries matches 3 documents, the range built for keys [1, 2] matches 2, and the first query still prints key 1.
- Actual: The OR matches 1 (`expected:<3> but was:<1>`), the range matches 1 (`expected:<2> but was:<1>`), and the query prints key 2.

## Files

- [src/org/apache/lucene/search/TestBinaryPointQueryArrayAliasing.java](src/org/apache/lucene/search/TestBinaryPointQueryArrayAliasing.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestBinaryPointQueryArrayAliasing
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestBinaryPointQueryArrayOwnership.java](TestBinaryPointQueryArrayOwnership.java)
- [TestBinaryPointQueryArrayAliasing.java](TestBinaryPointQueryArrayAliasing.java)
- [TestBinaryPointQueryOwnershipV1.java](TestBinaryPointQueryOwnershipV1.java)
- [TestBinaryPointOneDimOwnership.java](TestBinaryPointOneDimOwnership.java)
- [TestAudit4BinaryPointOwnership.java](TestAudit4BinaryPointOwnership.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
