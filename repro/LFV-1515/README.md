# Sandbox TermAutomatonQuery's explain() passes the hit's score to the similarity as the match frequency, so IndexSearcher.explain reports a value that is not the hit's score (0.028268427 for a hit that scores 0.13076457); the search's hits and scores are not affected; every release from 9.6.0 to 10.6.0, branch_11x and main

Finding LFV-1515 · Area: sandbox / search: TermAutomatonQuery (experimental), TermAutomatonWeight.explain · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1515.html](../../bugs/LFV-1515.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one document, 'wifi', search it with a TermAutomatonQuery that accepts 'wifi' or 'wi fi', and ask IndexSearcher.explain for the hit. You expect the explanation's value to be the hit's score, 0.13076457. Instead it is 0.028268427 and its text reads 'score(freq=0.13076457)': explain() gave the similarity the hit's score where the match frequency (1) belongs, so the reported value is the similarity's score of the score; the search itself is right.

## Expected vs actual

- Expected: The explanation's value equals the hit's score, 0.13076457.
- Actual: The explanation's value is 0.028268427 (`expected:<0.13076457> but was:<0.028268427>` at line 54); its text reads `score(freq=0.13076457)`.

## Files

- [src/org/apache/lucene/sandbox/search/TestTermAutomatonQueryExplainValue.java](src/org/apache/lucene/sandbox/search/TestTermAutomatonQueryExplainValue.java) (JUnit test)

## Run it

- Tested on Lucene main 1cfa86310f76 (a commit of 2026-11-17): reproduced, with assertions on and off.
- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- What each build does: the test fails the same way on Lucene main, on the 11.0 release branch (branch_11x daa54f2e0cd3) and with the 10.6.0 release jars, with and without assertions; the finding's record lists every release from 9.6.0 on. What the test does: it indexes one document, 'wifi', builds a TermAutomatonQuery that accepts wifi or wi fi (an automaton that rewrite() keeps), searches with a plain IndexSearcher, so with the default BM25 similarity, and expects the value of IndexSearcher.explain for the hit to equal the hit's score. It fails with 'expected:&lt;0.13076457&gt; but was:&lt;0.028268427&gt;': 0.13076457 is the hit's score and 0.028268427 the explained value. The test's input has no randomness; the test framework draws the default codec from the seed, and the two numbers were the same under every seed that was run. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put the six 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-facet, lucene-queries, lucene-sandbox) in place of the six $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/sandbox.
- This finding was re-run on Lucene main 7b8418faa2ff on 2026-11-20: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;0.13076457&gt; but was:&lt;0.028268427&gt; (the hit's score, then the explained value).
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
     execute --select-class org.apache.lucene.sandbox.search.TestTermAutomatonQueryExplainValue
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestTermAutomatonQueryExplainValue.java](TestTermAutomatonQueryExplainValue.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
