# With the new opt-in setting maxNegativeDisjuncts above 0 (0 in every constructor that existed before), a Monitor over TermFilteredPresearcher or MultipassTermFilteredPresearcher can silently fail to report a registered query that matches the document, when a MUST_NOT clause is a BooleanQuery with a required clause that reports nothing to the QueryVisitor (run: a TermInSetQuery without terms, an inverted or empty TermRangeQuery, a RegexpQuery or AutomatonQuery with an empty language) beside another clause: the negated group is indexed as the negation of the other clause only (main and branch_11x, both unreleased; in no release)

Finding LFV-1588 · Area: monitor / presearcher query analysis: the negative disjuncts of the term presearchers (QueryAnalyzer's negation mode, switched on by the setting maxNegativeDisjuncts of TermFilteredPresearcher and MultipassTermFilteredPresearcher); new on main and branch_11x, both unreleased · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1588.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1588.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a Monitor over TermFilteredPresearcher or MultipassTermFilteredPresearcher with the new setting maxNegativeDisjuncts above 0 (on main and branch_11x since 2026-11-23, in no release; 0 in every constructor that existed before) and register a query such as '\*:\* -(+tag:red +id:())', whose negated group requires tag:red and an id set that has no terms. The empty set matches nothing, so the negated group excludes nothing and the query matches every document (IndexSearcher counts 1 for the document {tag:red}), and you expect Monitor.match to report the query for that document; the class javadoc of the mode says that negations that cannot be expressed exactly, 'such as phrases or wildcards', 'still select the query for every document'. Instead it reports 0 matches and runs 0 queries, with no error: the analyzer builds the negated group from the clauses that report something to the QueryVisitor, so the query is indexed like '\*:\* -tag:red' and a document that holds tag:red, matched alone, does not select it; with the setting 0 the same builds report it.

## Expected vs actual

- Expected: `Monitor.match` reports the registered query for the document `{tag: red}`: `getMatchCount()` is 1, as `IndexSearcher` over a `MemoryIndex` of the same document counts 1 (the test's first assertion, which passes).
- Actual: `getMatchCount()` is 0 (`expected:<1> but was:<0>` at line 44). No exception, with assertions on and off.

## Files

- [src/org/apache/lucene/monitor/TestNegatedConjunctionWithEmptyClause.java](src/org/apache/lucene/monitor/TestNegatedConjunctionWithEmptyClause.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene branch_11x 784a2d5d03d4 (a commit of 2026-11-23): reproduced.
- It needs the opt-in setting maxNegativeDisjuncts above 0: the test gives 1 as the last argument of the four-argument TermFilteredPresearcher constructor. The setting is new and unreleased: main has it since 894d24e9b0c7 and branch_11x since 784a2d5d03d4, both commits of 2026-11-23, no release had it when read on 2026-11-26, and it is 0 in every constructor that existed before. New in 11.0 means here a defect in a feature that only branch_11x and main have, not a regression of anything that 10.6.0 does. What the test does: it registers the query \*:\* -(+tag:red +id:()), whose negated group requires tag:red and a term set without terms, and matches the document {tag: red}; IndexSearcher over a MemoryIndex of the document counts 1 (the first assertion, which passes), and Monitor.match reports 0 matches: java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;0&gt;. The control is the setting at 0: with only the constructor's last argument changed from 1 to 0 the test passes on the same builds. Run on 2026-11-25 with the block below, as printed for assertions on and with -da -dsa -Dtests.asserts=false for assertions off: 1 test found, 1 test failed, on main 8506598d7a98 and, with V=11.0.0-SNAPSHOT, on branch_11x 55ef56a69344; run again on 2026-11-26, the same result on main 77d56fefb355 and, with V=11.0.0-SNAPSHOT, on branch_11x 4e8fd3dc3bc4. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there. The test cannot be compiled on a release, because no release has the four-argument constructor: against the 10.6.0 release jars javac reports one error, no suitable constructor found for TermFilteredPresearcher(TermWeightor,List&lt;Object&gt;,Set&lt;Object&gt;,int), and no test is found; so this page has no recipe for a release. The test has no random() call of its own; the test framework draws its environment from the seed. For the Gradle alternative at the end of this page the module is lucene/monitor: with the test copied to lucene/monitor/src/test/org/apache/lucene/monitor/ of the checkout, ./gradlew -p lucene/monitor test --tests TestNegatedConjunctionWithEmptyClause -Ptests.seed=DEADBEEF reported 1 test, 1 failure in checkouts of main 8506598d7a98 and of branch_11x 55ef56a69344 on 2026-11-25, with -Ptests.asserts=true and with -Ptests.asserts=false.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestNegatedConjunctionWithEmptyClause
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestNegatedConjunctionWithEmptyClause.java](TestNegatedConjunctionWithEmptyClause.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
