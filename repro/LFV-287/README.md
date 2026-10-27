# Intervals.notContaining drops minuend intervals that a subtrahend interval overlaps on the right edge or encloses, though they contain no subtrahend interval

Bug LFV-287 · Area: queries / intervals (NotContainingIntervalsSource, Intervals.notContaining) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with Intervals.notContaining(minuend, subtrahend), for example notContaining(phrase(a,b), phrase(b,c)) on the text 'a b c', or notContaining(term york, phrase(new,york)) on 'new york'. You expect the minuend interval back, because the javadoc says notContaining 'Returns intervals from the minuend that do not contain intervals of the subtrahend', and 'a b' does not contain 'b c' (and one position cannot contain two). Instead the iterator keeps a minuend interval only if the next subtrahend interval starts after its end, so both queries return no interval and the document is not a hit, while the mirror-image overlap on the left edge (notContaining(phrase(b,c), phrase(a,b))) is kept.

## Expected vs actual

- Expected: You expect the minuend interval back, because the javadoc says notContaining 'Returns intervals from the minuend that do not contain intervals of the subtrahend', and 'a b' does not contain 'b c' (and one position cannot contain two).
- Actual: The iterator keeps a minuend interval only if the next subtrahend interval starts after its end, so both queries return no interval and the document is not a hit, while the mirror-image overlap on the left edge (notContaining(phrase(b,c), phrase(a,b))) is kept.

## Files

- [src/org/apache/lucene/queries/intervals/TestNotContainingPartialOverlap.java](src/org/apache/lucene/queries/intervals/TestNotContainingPartialOverlap.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.queries.intervals.TestNotContainingPartialOverlap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
