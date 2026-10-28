# Intervals.within and notWithin are documented as opposites, but a multi-position source interval only partly inside the n-position window around the reference matches neither (documentation / API-contract defect)

Bug LFV-830 · Area: queries / intervals (Intervals.within / Intervals.notWithin and their javadoc; queryparser fn:within / fn:notWithin docs) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'the new york times' and run within(phrase(new, york), 1, times) and notWithin(phrase(new, york), 1, times), and you expect exactly one of them to match, because the javadoc says within returns source intervals that 'appear within a set number of positions' of the reference and notWithin those that 'do not appear within' it, and the fn:within / fn:notWithin docs give both the same 'maximum positions' parameter. Instead neither matches: within keeps an interval only if all of it lies inside the window [r.start-n, r.end+n] (here [2,4]), while notWithin drops it if any part touches that window, so 'new york' at [1,2], which touches the window without lying inside it, is in neither result (within needs n = 2 for a phrase directly next to the reference). In the same way, on 'new york' both within(phrase(new, york), 0, york) and notWithin(phrase(new, york), 0, york) return nothing, and no doc says that a partly-near interval matches neither.

## Expected vs actual

- Expected: You expect exactly one of them to match, because the javadoc says within returns source intervals that 'appear within a set number of positions' of the reference and notWithin those that 'do not appear within' it, and the fn:within / fn:notWithin docs give both the same 'maximum positions' parameter.
- Actual: Neither matches: within keeps an interval only if all of it lies inside the window [r.start-n, r.end+n] (here [2,4]), while notWithin drops it if any part touches that window, so 'new york' at [1,2], which touches the window without lying inside it, is in neither result (within needs n = 2 for a phrase directly next to the reference).

## Files

- [src/org/apache/lucene/queries/intervals/TestWithinNotWithinPartlyNear.java](src/org/apache/lucene/queries/intervals/TestWithinNotWithinPartlyNear.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestWithinNotWithinPartlyNear
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
