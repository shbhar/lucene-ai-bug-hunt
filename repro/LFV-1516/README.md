# Sandbox TermAutomatonQuery counts a match twice when an ANY transition is crossed at a position that holds a query term, so two documents of equal length with one match each score differently, and an ANY transition that a match can take repeatedly doubles the scorer's state list at every such position until the heap is exhausted (26 occurrences of a query term at 1 GB); every release since 4.10.0, branch_11x and main

Finding LFV-1516 · Area: sandbox / search: TermAutomatonQuery (experimental), TermAutomatonScorer.countMatches · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1516.html](../../bugs/LFV-1516.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a TermAutomatonQuery for '"comes", any one token, "sun"' or '"comes moon"' and search two documents of three tokens each, 'comes the sun' and 'comes sun sun'. Each holds the match exactly once, so you expect equal scores; instead 'comes sun sun' scores 0.22790195 and 'comes the sun' 0.16574687, because the scorer counts the one match of 'comes sun sun' twice: the token under the ANY transition is itself a query term, and the scorer follows the ANY transition for that position a second time. The duplicate doubles again at every further such position: with the query '"a", any number of tokens, "b"' one document with 'a' 40 times and then 'b' ends the search with OutOfMemoryError at a 1 GB heap (26 occurrences are enough, 25 complete). The defect does not change which documents match, and a query that is a single chain is rewritten to a phrase query and is not affected. Every release since 4.10.0 does it, and branch_11x and main fail the test as the 10.6.0 release does.

## Expected vs actual

- Expected: Equal scores: both documents have three tokens and hold the match once.
- Actual: `comes sun sun` scores 0.22790195, `comes the sun` 0.16574687: `AssertionError: expected:<0.22790195> but was:<0.16574687>` at line 57, with assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/search/TestTermAutomatonQueryAnyMatchCount.java](src/org/apache/lucene/sandbox/search/TestTermAutomatonQueryAnyMatchCount.java) (JUnit test)

## Run it

- Tested on Lucene main 1cfa86310f76 (a commit of 2026-11-17): reproduced, with assertions on and off.
- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- What each build does: the test fails the same way on Lucene main, on the 11.0 release branch (branch_11x daa54f2e0cd3) and with the 10.6.0 release jars, with and without assertions; the same file, unchanged, also fails with the 10.5.2, 10.0.0 and 9.12.3 release jars, and the finding's record lists every release from 4.10.0 on. What the test does: it indexes two documents of three tokens, 'comes the sun' and 'comes sun sun', builds a TermAutomatonQuery for comes, any one token, sun, or comes moon (an automaton that rewrite() keeps), searches and expects equal scores, because each document holds the match once. It fails with 'expected:&lt;0.22790195&gt; but was:&lt;0.16574687&gt;', the scores of 'comes sun sun' and 'comes the sun': the one match of 'comes sun sun' is counted twice. The heap exhaustion that the finding also describes is not in this test. The test's input has no randomness; the test framework draws the default codec from the seed, and a plain program with the default codec gives the same two scores. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put the six 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-facet, lucene-queries, lucene-sandbox) in place of the six $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/sandbox.
- This finding was re-run on Lucene main b5a4546a5964 on 2026-11-22: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;0.22790195&gt; but was:&lt;0.16574687&gt;.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestTermAutomatonQueryAnyMatchCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestTermAutomatonQueryAnyMatchCount.java](TestTermAutomatonQueryAnyMatchCount.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
