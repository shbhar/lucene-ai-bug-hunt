# LongField.newDistanceFeatureQuery and LatLonPoint.newDistanceFeatureQuery build queries whose toString(String field) prints the caller's default-field argument as the query's own field (the parameter shadows this.field), so toString() shows 'field=' with no name and toString("x") claims field x

Finding LFV-1227 · Area: core / document: LongDistanceFeatureQuery and LatLonPointDistanceFeatureQuery (Query.toString) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1227.html](../../bugs/LFV-1227.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a boosting query with LongField.newDistanceFeatureQuery("price", 1f, 0, 1) or LatLonPoint.newDistanceFeatureQuery("geo", 1f, 18, 19, 1000) and print it. You expect the text to name the field, because Query.toString(String field) treats its argument as the default field to omit and toString() passes "", and the sibling queries of the same fields print 'price:' or 'geo:'. Instead you get 'LongDistanceFeatureQuery(field=,origin=0,pivotDistance=1)' and 'LatLonPointDistanceFeatureQuery(field=,originLat=18.0,...)', and toString("x") prints field=x, because both toString methods print their parameter, which shadows the instance field this.field; so two unequal queries on different fields print the same text.

## Expected vs actual

- Expected: `LongDistanceFeatureQuery(field=price,...)` and `LatLonPointDistanceFeatureQuery(field=geo,...)`: each text names its query's field.
- Actual: `LongDistanceFeatureQuery(field=,origin=0,pivotDistance=1)` and `LatLonPointDistanceFeatureQuery(field=,originLat=0.0,originLon=0.0,pivotDistance=1.0)`, assertions on and off.

## Files

- [src/org/apache/lucene/document/TestDistanceFeatureQueryToString.java](src/org/apache/lucene/document/TestDistanceFeatureQueryToString.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestDistanceFeatureQueryToString
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestDistanceFeatureQueryToString.java](TestDistanceFeatureQueryToString.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
