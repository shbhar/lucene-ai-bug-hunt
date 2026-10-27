# TermStates#equals compares only docFreq, so BooleanQuery still merges blended or fuzzy TermQuery clauses whose totalTermFreq differs, and similarities that read totalTermFreq then score them wrongly (the part of the #10309 fix, #12354, that was left out; main only)

Bug LFV-435 · Area: search / TermQuery equality and BooleanQuery clause de-duplication (blended and fuzzy TermStates) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You OR two fuzzy queries, FuzzyQuery('aaaab', 1) and FuzzyQuery('baaaa', 1), which both expand to the indexed term 'aaaa', and search with LMDirichletSimilarity; you expect each document to score the sum of the two clause scores, as it does under BM25 and as #12354 ('Do not merge term queries with different docFreq', the fix of #10309) set out to restore. Instead the blended rewrite gives both TermQuery(t:aaaa) clauses the same docFreq (the max, 3) but different totalTermFreq (the sum, 4 and 8); TermStates#equals compares only docFreq, so BooleanQuery merges them into (t:aaaa)^1.5 with the first clause's statistics, and docs 0-2 score 0.0011983 instead of 0.00059916, while the same query with the clauses swapped (equal per BooleanQuery#equals) scores them 0.0.

## Expected vs actual

- Expected: You expect each document to score the sum of the two clause scores, as it does under BM25 and as #12354 ('Do not merge term queries with different docFreq', the fix of #10309) set out to restore.
- Actual: The blended rewrite gives both TermQuery(t:aaaa) clauses the same docFreq (the max, 3) but different totalTermFreq (the sum, 4 and 8); TermStates#equals compares only docFreq, so BooleanQuery merges them into (t:aaaa)^1.5 with the first clause's statistics, and docs 0-2 score 0.0011983 instead of 0.00059916, while the same query with the clauses swapped (equal per BooleanQuery#equals) scores them 0.0.

## Files

- [src/org/apache/lucene/search/TestBlendedTermStatesMerge.java](src/org/apache/lucene/search/TestBlendedTermStatesMerge.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestBlendedTermStatesMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
