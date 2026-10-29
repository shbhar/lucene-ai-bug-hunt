# Surround query parser ignores a ^ weight written directly on the operand of a field prefix (title: lucene^3, title: body: lucene^3, body: or(a, b)^2), because FieldsQuery builds its operand with makeLuceneQueryFieldNoBoost

Bug LFV-686 · Area: queryparser / surround (FieldsQuery field prefixes and ^ weights) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-686.html](../../bugs/LFV-686.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse the surround query or(title: lucene^3, body: lucene), whose parser documents 'boosting (via ^)', and expect the title match to count three times, exactly like the same weight on the parenthesised field query or((title: lucene)^3, body: lucene). Instead the weight is kept on the parsed term (toString shows title:lucene^3.0), but FieldsQuery.makeLuceneQueryNoBoost builds its operand with makeLuceneQueryFieldNoBoost, so no BoostQuery is created: the title document scores 0.13076457 instead of 0.39229375, no more than the body-only match, which the weight was meant to outrank. Every weight on the direct operand of a field prefix is dropped (a term, an OR, a distance query, with one field or several); only scores and ranking change, never which documents match.

The test shows that the surround parser drops a `^` weight written on the operand of a field prefix: `or(title: lucene^3, body: lucene)` scores as if unweighted.

## Expected vs actual

Expected: the score of `or((title: lucene)^3, body: lucene)`, 0.39229375. Actual: 0.13076457 (one third of it: the ^3 is not applied), with -ea and -da.

## Files

- [src/org/apache/lucene/queryparser/surround/query/TestSrndFieldPrefixOperandWeight.java](src/org/apache/lucene/queryparser/surround/query/TestSrndFieldPrefixOperandWeight.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queryparser.surround.query.TestSrndFieldPrefixOperandWeight
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
