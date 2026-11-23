# Sandbox TermAutomatonQuery throws IllegalStateException ('Cannot call docFreq() when needsStats=false') whenever it runs without scores (IndexSearcher.count, a FILTER or MUST_NOT clause, ConstantScoreQuery, a search sorted by index order), for every automaton that rewrite() keeps, while the scoring search of the same query returns its hits; every release from 8.0.0 to 10.6.0, branch_11x and main; known outside Lucene

Finding LFV-1514 · Area: sandbox / search: TermAutomatonQuery (createWeight and its Weight's constructor) · Priority: low · Impact: crash · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one document 'a b', build a TermAutomatonQuery that accepts the phrase 'a b' or the single term 'c', search with it and get the hit, then call IndexSearcher.count with the same query and expect 1. Instead count throws IllegalStateException 'Cannot call docFreq() when needsStats=false': when no scores are needed, createWeight builds each term's TermStates without statistics, and the Weight's constructor reads docFreq() of every term whatever the score mode. The same happens for the query as a FILTER or MUST_NOT clause, under ConstantScoreQuery and in a search sorted by index order; only automata that rewrite() turns into a term, phrase or multi-phrase query are spared. Not new in 11: every release since 8.0.0 does it.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Count throws IllegalStateException 'Cannot call docFreq() when needsStats=false': when no scores are needed, createWeight builds each term's TermStates without statistics, and the Weight's constructor reads docFreq() of every term whatever the score mode.

## Files

- [src/org/apache/lucene/sandbox/search/TestTermAutomatonQueryCount.java](src/org/apache/lucene/sandbox/search/TestTermAutomatonQueryCount.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on the 11.0 release branch (branch_11x daa54f2e0cd3) and on Lucene main, with and without assertions; the finding's record lists every release from 8.0.0 on. Known outside Lucene only: two user posts of 2022 about a downstream product show the same exception message, and that product's public source carries a workaround class; no Lucene issue, pull request or list thread was found that describes it. What the test does: it indexes one document with the text 'a b', builds a TermAutomatonQuery that accepts the phrase 'a b' or the single term 'c' (an automaton that rewrite() keeps) and expects IndexSearcher.count to return 1. The count throws java.lang.IllegalStateException: Cannot call docFreq() when needsStats=false, while the query's Weight is built; in the recorded runs a scoring search with the same query returned the document. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put the six 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-facet, lucene-queries, lucene-sandbox) in place of the six $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/sandbox.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.IllegalStateException: Cannot call docFreq() when needsStats=false, thrown out of IndexSearcher.count.
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

# run 1: assertions off (production-like), seed C0FFEE
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.sandbox.search.TestTermAutomatonQueryCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/termautomatonquery-non-scoring-use/](../../models/termautomatonquery-non-scoring-use/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
