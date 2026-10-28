# AxiomaticF1EXP, F1LOG, F3EXP and F3LOG explain tf as 'computed as 1 + log(1 + log(freq))' of the raw freq, but the value they show is 1 + log(1 + log(freq + 1)); the score itself is correct

Bug LFV-920 · Area: core / search similarities (Axiomatic F1/F3 term-frequency explanation, tfExplain) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with AxiomaticF1EXP (or F1LOG, F3EXP, F3LOG) and call IndexSearcher.explain on a hit. The tf node says 'tf, term frequency computed as 1 + log(1 + log(freq)) from:' and its only child is 'freq, number of occurrences of term in the document', the raw count, so you expect its value to be that formula of that child. Instead tf first adds 1 to freq (since LUCENE-8010, 2017), so for freq = 1 the node shows 1.5265890 where the stated formula gives 1.0, and for freq = 3 it shows 1.8697417 instead of 1.7412763; the score is right and only the formula text is stale.

## Expected vs actual

- Expected: You expect its value to be that formula of that child.
- Actual: tf first adds 1 to freq (since LUCENE-8010, 2017), so for freq = 1 the node shows 1.5265890 where the stated formula gives 1.0, and for freq = 3 it shows 1.8697417 instead of 1.7412763; the score is right and only the formula text is stale.

## Files

- [src/org/apache/lucene/search/similarities/TestAxiomaticTfExplainFormula.java](src/org/apache/lucene/search/similarities/TestAxiomaticTfExplainFormula.java) (JUnit test)

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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.similarities.TestAxiomaticTfExplainFormula
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/explanation-formula-strings-vs-their-own-operand-values/](../../models/explanation-formula-strings-vs-their-own-operand-values/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
