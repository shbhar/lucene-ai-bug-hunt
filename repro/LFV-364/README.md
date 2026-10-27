# RegExp builds an automaton that doubles with every stacked '+' ('ab' followed by n '+' gives 2^n+2 states for the 3-state language ab+), a 10.0 regression that exhausts a 1 GB heap at about 20 '+'

Bug LFV-364 · Area: util / automaton (RegExp, Operations.repeat; reached from RegexpQuery and the classic and flexible query parsers) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a RegexpQuery for 'ab' followed by n '+' (directly, or by typing /ab+++.../ into the classic or flexible query parser) and expect a tiny automaton, because '+' means one or more occurrences, so the language is just ab+ (3 states, which is what 9.12.3 builds for any n). Instead the automaton doubles with every '+' (2^n+2 states: 16,386 at n=14, 262,146 at n=18), because RegExp turns each '+' into Operations.repeat(a, 1), which is built as a followed by a* and so copies a, and since 10.0 nothing minimizes in between; with a 1 GB heap about 20 '+' (a 22-character regexp) end in OutOfMemoryError while the query is still being constructed. Stacked {1,} and nested ((x)+)+ double the same way; stacked '*' and '?' do not.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The automaton doubles with every '+' (2^n+2 states: 16,386 at n=14, 262,146 at n=18), because RegExp turns each '+' into Operations.repeat(a, 1), which is built as a followed by a* and so copies a, and since 10.0 nothing minimizes in between; with a 1 GB heap about 20 '+' (a 22-character regexp) end in OutOfMemoryError while the query is still being constructed.

## Files

- [src/org/apache/lucene/queryparser/TestRegexpNestedPlusBlowup.java](src/org/apache/lucene/queryparser/TestRegexpNestedPlusBlowup.java) (JUnit test)
- [src/RegexpPlusScaleProbe.java](src/RegexpPlusScaleProbe.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.queryparser.TestRegexpNestedPlusBlowup

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" RegexpPlusScaleProbe
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
