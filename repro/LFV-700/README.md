# BlendedInfixSuggester loses a distinct suggestion when a duplicate entry arrives while its result set is full: boundedTreeAdd evicts the lowest result before TreeSet.add rejects the duplicate, so lookup returns num-1 suggestions, or a lower-ranked fetched suggestion in place of a higher-ranked one

Finding LFV-700 · Area: suggest / analyzing: BlendedInfixSuggester result collection · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-700.html](../../bugs/LFV-700.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fix in progress, by sjs004 (PR #16993) on 2026-11-06 (https://github.com/apache/lucene/pull/16993). PR #16993 (sjs004, open, head 4327468c86f9) references this bug. It makes BlendedInfixSuggester's bounded add evict the lowest entry only after a new suggestion was actually added, so a rejected duplicate no longer drops a distinct suggestion. No review yet. Our minimal repro passes with the PR's changed source file shadowed on main f794ff5a505c, with and without assertions, and fails on stock f794ff5a505c (2026-11-08). This site files nothing upstream.

## What it shows

You build a BlendedInfixSuggester with its default settings (POSITION_LINEAR, numFactor 10) from {the lake:1000, lake:950, lake:950, my lake:920} and call lookup("lake", false, 2); blending gives lake 950, the lake 900 and my lake 828, num=3 returns [lake/950, the lake/900, my lake/828], so you expect [lake/950, the lake/900]. Instead you get [lake/950, my lake/828], and without 'my lake' only [lake/950]. When the bounded result set is full, boundedTreeAdd (BlendedInfixSuggester.java:294-306) removes the lowest result with pollFirst() before it calls results.add(result), and that add does nothing for the second 'lake' because LookUpComparator (:383-407) already holds an equal element, so 'the lake' is lost and a later, lower-ranked hit takes its slot.

The test shows that a default `BlendedInfixSuggester` drops a distinct suggestion when a duplicate arrives while its result set is full: `boundedTreeAdd` evicts the lowest result before `TreeSet.add` rejects the duplicate.

## Expected vs actual

For {the lake:1000, lake:950, lake:950, my lake:920}, `lookup("lake", false, 2)` should be the top 2 of the num=3 result, `[lake/950, the lake/900]`; actual `[lake/950, my lake/828]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestBlendedInfixDuplicateEviction.java](src/org/apache/lucene/search/suggest/analyzing/TestBlendedInfixDuplicateEviction.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
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
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestBlendedInfixDuplicateEviction
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
