# The intervals disjunction rewrite looks only one level into an or, so an or inside a maxwidth, fixField or extend wrapper (or inside an ordered) that is itself one alternative of another or is never split, and phrase, maxgaps or containedBy miss hits: phrase(x, or(maxwidth(5, or("b c", b)), y), d) misses 'x b c d', which phrase(x, maxwidth(5, or("b c", b)), d) matches

Bug LFV-826 · Area: queries / intervals (gap-sensitive disjunction rewrite of LUCENE-8477: Disjunctions.splitDisjunctions and Disjunctions.pullUp vs an alternative of an or that has disjunctions of its own: maxwidth, extend, fixField, ordered) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-826.html](../../bugs/LFV-826.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build phrase(x, or(W, y), d) with W = maxwidth(5, or(phrase(b, c), b)) and expect the hit on 'x b c d' that phrase(x, W, d) finds, because Intervals.or 'Automatically rewrites if wrapped by an interval source that is sensitive to internal gaps' and adding an alternative y that occurs in no document cannot remove a match. Instead the rewrite (Disjunctions.splitDisjunctions) asks the outer or for its alternatives only one level deep, so W reaches the phrase whole (here grouped with y into one minimizing or, because W's shortest match is one position), the inner or drops the interval 'b c' in favour of the 'b' it contains, and the query returns no hits. The same happens with fixField(g, or(...)) or extend(or(...), 0, 0) in place of maxwidth, with containedBy or maxgaps as the parent, and with an ordered alternative whose own or has alternatives of different lengths.

The test shows that `phrase(x, or(maxwidth(5, or(phrase(b, c), b)), y), d)` misses `x b c d`, because the disjunction rewrite splits the outer `or()` only one level deep and never splits the `or()` inside `maxwidth`.

## Expected vs actual

Expected: 1 hit (`y` occurs nowhere, and `phrase(x, maxwidth(5, or(phrase(b, c), b)), d)` matches). Actual: 0 hits (`expected:<1> but was:<0>`).

## Files

- [src/org/apache/lucene/queries/intervals/TestPhraseOverOrOfWrappedOr.java](src/org/apache/lucene/queries/intervals/TestPhraseOverOrOfWrappedOr.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestPhraseOverOrOfWrappedOr
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
