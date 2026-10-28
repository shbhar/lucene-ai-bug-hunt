# ContextQuery throws AssertionError 'input should not end with the context separator' (only with -ea) when its inner RegexCompletionQuery or FuzzyCompletionQuery accepts the empty string ('.*', 'z*', 'b?', or a fuzzy prefix within the edit budget), also through the automatic rewrite of a plain completion query on a ContextSuggestField: an incomplete fix of LUCENE-8287 (released since 5.3.0)

Bug LFV-821 · Area: suggest / document (NRT) suggester (ContextQuery: ContextCompletionWeight.setInnerWeight when the inner automaton accepts the empty string) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index suggestions in a ContextSuggestField and query them with a RegexCompletionQuery such as '.*' or 'z*' (or a FuzzyCompletionQuery with nonFuzzyPrefix 0 whose edit budget covers the whole prefix); you expect what a plain SuggestField returns for the same query, here every suggestion. Instead, with Java assertions enabled, the suggest call throws 'AssertionError: input should not end with the context separator': the inner automaton accepts the empty string, so the matched path ends right at the context separator, which ContextQuery's setInnerWeight asserts cannot happen. Upstream fixed this assertion in 2018 (LUCENE-8287) only for an empty regex text; with assertions disabled the results are correct.

## Expected vs actual

- Expected: You expect what a plain SuggestField returns for the same query, here every suggestion.
- Actual: With Java assertions enabled, the suggest call throws 'AssertionError: input should not end with the context separator': the inner automaton accepts the empty string, so the matched path ends right at the context separator, which ContextQuery's setInnerWeight asserts cannot happen.

## Files

- [src/org/apache/lucene/search/suggest/document/TestContextQueryEmptyAcceptingInner.java](src/org/apache/lucene/search/suggest/document/TestContextQueryEmptyAcceptingInner.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.document.TestContextQueryEmptyAcceptingInner
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
