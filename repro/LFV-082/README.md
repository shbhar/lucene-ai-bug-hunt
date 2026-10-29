# Flexible StandardQueryParser narrows out-of-range int literals with intValue, so i:[2147483648 TO *] matches every document with the field

Bug LFV-082 · Area: queryparser / flexible · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse a point query whose literal lies outside the field's int range, such as i:[2147483648 TO *], i&gt;=2147483648 or i:2147483648, with the flexible StandardQueryParser and an Integer PointsConfig. You expect no match, because no int value reaches that bound. Instead PointRangeQueryNodeProcessor and PointQueryNodeProcessor narrow the parsed number with Number.intValue, which wraps it, so the range queries match all 5 documents of the repro and i:2147483648 matches the Integer.MIN_VALUE document.

The test shows that the flexible StandardQueryParser with an Integer PointsConfig narrows the out-of-range literal in i:[2147483648 TO *] with intValue(), so it parses to i:[-2147483648 TO 2147483647].

## Expected vs actual

Expected: 0 hits on a one-document index (i = 0), since no int is &gt;= 2^31. Actual (-ea and -da): 1 hit, "i:[-2147483648 TO 2147483647] expected:&lt;0&gt; but was:&lt;1&gt;".

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestPointRangeOutOfIntRange.java](src/org/apache/lucene/queryparser/flexible/standard/TestPointRangeOutOfIntRange.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestPointRangeOutOfIntRange
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
