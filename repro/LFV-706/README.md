# FSTCompletionLookup ignores exactMatchFirst=false for by-weight lookups after build and load, so an exact match is always ranked first

Bug LFV-706 · Area: suggest / FST completion suggester (FSTCompletionLookup build/load exactMatchFirst wiring) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-706.html](../../bugs/LFV-706.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an FSTCompletionLookup(dir, "fst", 10, false) from star (weight 1), star wars (100) and start (50) and ask lookup("star", true, 3) for the most popular completions first. You expect [star wars/6, start/3, star/0], because the constructor javadoc says that with exactMatchFirst=false exact matches 'appear in the order of discretized weight and alphabetical within the bucket'. Instead you get [star/0, star wars/6, start/3], with no error, after build and equally after store and load: both paths create the internal by-weight FSTCompletion with its exact-first default, and the flag reaches only the alphabetical completion, where it changes nothing.

The test shows that `FSTCompletionLookup(dir, "fst", 10, false)` still promotes the exact match in by-weight lookups after `build()`: for `star` (weight 1) and `star wars` (weight 100), `lookup("star", true, 2)` puts `star` first.

## Expected vs actual

Expected `[star wars/5, star/0]` (order of discretized weight, as the `exactMatchFirst` javadoc says); actual `[star/0, star wars/5]`, with `-ea` and `-da`; passes with the two-site fix (by-weight view built with `exactMatchFirst`).

## Files

- [src/org/apache/lucene/search/suggest/fst/TestFSTCompletionLookupExactMatchFirstFalse.java](src/org/apache/lucene/search/suggest/fst/TestFSTCompletionLookupExactMatchFirstFalse.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.fst.TestFSTCompletionLookupExactMatchFirstFalse
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
