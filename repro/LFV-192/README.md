# AutomatonQuery.ramBytesUsed is computed once at construction, so a query that runs on an NFA (WildcardQuery and RegexpQuery by default on main, opt-in NFA queries since 10.0.0) under-reports the DFA states it builds while searching, and an LRUQueryCache can hold far more heap than its RAM budget

Bug LFV-192 · Area: search / AutomatonQuery RAM accounting (NFARunAutomaton lazily built DFA states; LRUQueryCache RAM budget) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a WildcardQuery or RegexpQuery whose automaton is not deterministic, for example '*a?????????' or 'a*b??????????', and expect its ramBytesUsed to estimate the heap the query holds, which is what the Accountable javadoc says and what LRUQueryCache uses to stay within maxRamBytesUsed. On main such a query runs on an NFARunAutomaton that builds DFA states lazily during the search and keeps them on the query object, but AutomatonQuery stored ramBytesUsed in a final field when it was constructed, so after one search the value is 40x to 310x too small (5,674 bytes reported vs 228,680 retained; 6,714 vs 2,081,152). An LRUQueryCache with a 1 MiB budget then keeps 4 such queries as keys that retain 2.7 to 7.4 MB while it accounts about 36 KB in total and evicts nothing; the matching documents are correct.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/search/TestAutomatonQueryRamAfterNfaRun.java](src/org/apache/lucene/search/TestAutomatonQueryRamAfterNfaRun.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestAutomatonQueryRamAfterNfaRun
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
