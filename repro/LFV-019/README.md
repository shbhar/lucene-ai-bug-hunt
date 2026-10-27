# RegExp no longer parses an escaped range start such as [\.-z] as a range

Bug LFV-019 · Area: util / automaton · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-019.html](../../bugs/LFV-019.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a RegexpQuery or RegExp whose character class starts a range with an escaped character, such as [\.-z]. You expect it to match the range from '.' to 'z', as in 10.1 and java.util.regex. Instead, since 10.2.0, it is parsed as the three characters '.', '-' and 'z', so the query silently matches different terms.

## Expected vs actual

- Expected: `[\.-z]` behaves like `[.-z]`: `a` and `/` match, `-` does not, and the tree is one range from `.` to `z` (as on 10.1.0).
- Actual: `a=false /=false -=true`: the tree is a class of three characters, `.`, `-` and `z`; a `RegexpQuery` over the six terms counts 3 hits instead of 5.

## Files

- [src/TestEscapedRangeStartInCharClass.java](src/TestEscapedRangeStartInCharClass.java) (JUnit test)
- [src/TestEscapedRangeStartSearch.java](src/TestEscapedRangeStartSearch.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 9 test(s) run, 6 failed (testEscapedAndUnescapedStartSameLanguage, testEscapedRangeStart, testExhaustiveAsciiDifferential, testToStringRoundTripOfRange, testClassicQueryParserEscapedStart, testRegexpQueryEscapedStart).
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestEscapedRangeStartInCharClass \
       --select-class TestEscapedRangeStartSearch
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [EscapedRangeStart.java](EscapedRangeStart.java)
- [TestRegExpEscapedRangeStart.java](TestRegExpEscapedRangeStart.java)
- [test-parsing.diff](test-parsing.diff)
- [EscapedRangeExamples.java](EscapedRangeExamples.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
