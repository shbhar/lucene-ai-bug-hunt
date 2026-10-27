# Flexible StandardQueryParser narrows out-of-range int literals with intValue, so i:[2147483648 TO *] matches every document with the field

Bug LFV-082 · Area: queryparser / flexible · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse a point query whose literal lies outside the field's int range, such as i:[2147483648 TO *], i&gt;=2147483648 or i:2147483648, with the flexible StandardQueryParser and an Integer PointsConfig. You expect no match, because no int value reaches that bound. Instead PointRangeQueryNodeProcessor and PointQueryNodeProcessor narrow the parsed number with Number.intValue, which wraps it, so the range queries match all 5 documents of the repro and i:2147483648 matches the Integer.MIN_VALUE document.

## Expected vs actual

- Expected: You expect no match, because no int value reaches that bound.
- Actual: PointRangeQueryNodeProcessor and PointQueryNodeProcessor narrow the parsed number with Number.intValue, which wraps it, so the range queries match all 5 documents of the repro and i:2147483648 matches the Integer.MIN_VALUE document.

## Files

- [src/TestF4RangeLimitLeads.java](src/TestF4RangeLimitLeads.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF -Dtests.method=testOutOfRangeLiteralNarrowing \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestF4RangeLimitLeads
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
