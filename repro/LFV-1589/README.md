# Monitor.register with the id of a registered query and a new query that QueryDecomposer splits into no part (run: a BooleanQuery with only MUST_NOT clauses and minimumNumberShouldMatch 0 or 1, a BooleanQuery without clauses, a BoostQuery or a single MUST clause around one, a DisjunctionMaxQuery without disjuncts) returns normally and leaves the old query registered: Monitor.match keeps reporting the id for the documents the old query matches, and a persistent Monitor's getQuery returns the old query, also after a reopen (the test, which checks Monitor.match, fails on each release from 8.2.0 to 10.6.0 and on branch_11x and main)

Finding LFV-1589 · Area: monitor / query index updates (WritableQueryIndex.commitWithoutNotify, reached through both Monitor.register overloads, with QueryDecomposer for the parts of the new query) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1589.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1589.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register a query under an id with Monitor.register and later call Monitor.register again with the same id and a new query, as the module's own test testUpdatesOverwriteOldQueries does with two term queries. If the new query is one that the Monitor's QueryDecomposer splits into no part, such as a BooleanQuery with only MUST_NOT clauses and minimumNumberShouldMatch 0 or 1 (-f:pear, which the classic QueryParser builds from 'NOT pear') or a BooleanQuery without clauses (which it builds from a string of stop words only when the analyzer removes them), you expect the id to answer for the new query, which matches no document, so that nothing is reported under it any more. Instead the call returns normally and the old query stays registered: Monitor.match keeps reporting the id for the documents the old query matches, and a Monitor that stores its queries keeps returning the old query from getQuery, also after it is closed and reopened, while the same call with MatchNoDocsQuery as the new query does replace the old one (the javadoc of register says only 'Add new queries to the monitor'; the replacement is what the module's code does for each id it adds documents for, and the module has a test named for it).

## Expected vs actual

- Expected: `Monitor.match` reports nothing for the document `f:"apple"`: `getMatchCount()` is 0, because `q1` was registered again with `-f:pear`, which matches no document.
- Actual: `getMatchCount()` is 1: `q1` is still reported for its old query `f:apple` (`expected:<0> but was:<1>` at line 29). No exception, with assertions on and off.

## Files

- [src/TestReRegisterWithQueryOfNoParts.java](src/TestReRegisterWithQueryOfNoParts.java) (JUnit test)

## Run it

- Tested on Lucene main 77d56fefb355 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off (12.0.0-SNAPSHOT).
- Tested on Lucene branch_11x 4e8fd3dc3bc4 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene main 8506598d7a98 (a commit of 2026-11-25): reproduced, with assertions on and off (12.0.0-SNAPSHOT).
- Tested on Lucene branch_11x 55ef56a69344 (a commit of 2026-11-25): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene main 894d24e9b0c7 (a commit of 2026-11-23): reproduced, with assertions on and off (12.0.0-SNAPSHOT).
- Tested on Lucene branch_11x 784a2d5d03d4 (a commit of 2026-11-23): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on builds of branch_11x (784a2d5d03d4, 55ef56a69344 and 4e8fd3dc3bc4) and of Lucene main (894d24e9b0c7, 8506598d7a98 and 77d56fefb355), with and without assertions. It needs a second Monitor.register call with the id of a query that is already registered (a first registration of such a query is not the finding) and a new query that the Monitor's QueryDecomposer splits into no part; each such query that ran matches no document, so no match is lost. What the test does: it registers q1 = f:apple, registers q1 again with -f:pear (a BooleanQuery whose one clause is MUST_NOT) and expects Monitor.match to report nothing for the document f:"apple"; getMatchCount() is 1: java.lang.AssertionError: expected:&lt;0&gt; but was:&lt;1&gt;. The same failure against the 8.2.0 release jars, the first release with the monitor module (the second recipe with V=8.2.0 and A=lucene-analyzers-common). A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there. Run the commands in a copy of this folder that also holds the jars they name (Maven Central). Both recipes were run on 2026-11-25, with -ea as printed and with -da: the first in a checkout of main 8506598d7a98 and, with V=11.0.0-SNAPSHOT, of branch_11x 55ef56a69344, the second with the 10.6.0 release jars and, with V=8.2.0 and A=lucene-analyzers-common, with the 8.2.0 release jars; Tests run: 1, Failures: 1 in each of the eight runs. Through Lucene's own build, run in both checkouts: with the file copied to lucene/monitor/src/test/ of the checkout, ./gradlew :lucene:monitor:test --tests TestReRegisterWithQueryOfNoParts -Ptests.seed=DEADBEEF -Ptests.asserts=true reports 1 test, 1 failure, and so does -Ptests.asserts=false. Monitor.deleteById(id) before the register call avoids it.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).

```bash
# Two recipes. The first is for a checkout of main or of branch_11x, the second for a release.

# JDK 25 on the PATH. $LUCENE: a checkout of apache/lucene (main, or branch_11x with V=11.0.0-SNAPSHOT below)
# whose four jars were built there with
#   ./gradlew :lucene:core:jar :lucene:analysis:common:jar :lucene:memory:jar :lucene:monitor:jar
# $REPRO: a folder with TestReRegisterWithQueryOfNoParts.java and, from Maven Central,
# junit-4.13.2.jar and hamcrest-core-1.3.jar
cd $REPRO
V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:junit-4.13.2.jar:hamcrest-core-1.3.jar
javac -d classes -cp "$CP" TestReRegisterWithQueryOfNoParts.java
java -ea -cp "classes:$CP" org.junit.runner.JUnitCore TestReRegisterWithQueryOfNoParts
# without assertions: the same java line with -da in place of -ea (the same failure)

# ---- for a release ----
# JDK 25 on the PATH. $REPRO: a folder with TestReRegisterWithQueryOfNoParts.java and, from Maven Central,
# junit-4.13.2.jar, hamcrest-core-1.3.jar and the four jars of the release: lucene-core, lucene-monitor,
# lucene-memory and lucene-analysis-common (for 8.2.0 the fourth is lucene-analyzers-common: set A below)
cd $REPRO
V=10.6.0
A=lucene-analysis-common
CP=lucene-core-$V.jar:lucene-monitor-$V.jar:lucene-memory-$V.jar:$A-$V.jar
CP=$CP:junit-4.13.2.jar:hamcrest-core-1.3.jar
javac -d classes -cp "$CP" TestReRegisterWithQueryOfNoParts.java
java -ea -cp "classes:$CP" org.junit.runner.JUnitCore TestReRegisterWithQueryOfNoParts
# without assertions: the same java line with -da in place of -ea (the same failure)
```

## Files shown on the finding page

- [TestReRegisterWithQueryOfNoParts.java](TestReRegisterWithQueryOfNoParts.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
