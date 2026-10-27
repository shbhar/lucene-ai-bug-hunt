# ContextQuery gives an exact context's boost to any longer indexed context that starts with it (exact 'red' boosts a suggestion indexed under 'redwood') when that longer context is matched through a shorter prefix context or addAllContexts, so document-suggester hits get wrong scores and a wrong order

Bug LFV-254 · Area: suggest / document (NRT) suggester (ContextQuery: ContextCompletionWeight.setNextMatch boost lookup over ContextSuggestField contexts) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-254.html](../../bugs/LFV-254.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index 'table lamp' (weight 5) under context 'redwood' and 'table cloth' (weight 4) under 'red' with ContextSuggestField, and query 'tab' with addAllContexts (or a prefix context 're') plus the exact context 'red' with boost 10; you expect 'table cloth' to score 4 x 10 = 40 and 'table lamp' its plain weight 5, because an exact context matches only an indexed context equal to it. Instead 'table lamp' scores 50 and ranks first: the boost lookup takes the longest query context whose characters equal the start of the indexed context and never checks that the indexed context ends there, so exact 'red' lends its boost to 'redwood'. Filtering is still right (exact 'red' alone does not return 'table lamp'); only scores and order are wrong.

## Expected vs actual

- Expected: `table cloth` 40 first and `table lamp` 5 (with the exact empty context: `table mat` 30 first, `table lamp` 5).
- Actual: `table lamp` scores 50 and comes first in all three claim tests: 3 of 5 fail, both controls pass.

## Files

- [src/org/apache/lucene/search/suggest/document/TestContextQueryExactContextBoost.java](src/org/apache/lucene/search/suggest/document/TestContextQueryExactContextBoost.java) (JUnit test)
- [src2/org/apache/lucene/search/suggest/document/TestContextQueryExactContextBoost.java](src2/org/apache/lucene/search/suggest/document/TestContextQueryExactContextBoost.java) (JUnit test)
- [src3/org/apache/lucene/search/suggest/document/TestContextQueryExactContextBoost.java](src3/org/apache/lucene/search/suggest/document/TestContextQueryExactContextBoost.java) (JUnit test)
- [src4/org/apache/lucene/search/suggest/document/TestContextQueryExactContextBoost.java](src4/org/apache/lucene/search/suggest/document/TestContextQueryExactContextBoost.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')
javac -d classes-src3 -cp "$CP" $(find src3 -name '*.java')
javac -d classes-src4 -cp "$CP" $(find src4 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestContextQueryExactContextBoost
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestContextQueryExactContextBoost
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src3:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestContextQueryExactContextBoost
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src4:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestContextQueryExactContextBoost
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestContextQueryExactContextBoost.java](TestContextQueryExactContextBoost.java)
- [TestExactContextBoostV1.java](TestExactContextBoostV1.java)
- [exact-context-boost.diff](exact-context-boost.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
