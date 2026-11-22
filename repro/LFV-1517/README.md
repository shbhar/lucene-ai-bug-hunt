# Sandbox TermAutomatonQuery.visit gives the QueryVisitor a Term with null bytes for an any-term transition, so UnifiedHighlighter.highlight and Monitor.register throw NullPointerException for a query built with addAnyTransition or the any-term token '*', while its search returns the hit; visit since 8.1.0, UnifiedHighlighter for every such query since 9.5.0, through 10.6.0, branch_11x and main

Finding LFV-1517 · Area: sandbox / search: TermAutomatonQuery.visit (seen through its consumers: highlighter / UnifiedHighlighter and monitor / query analysis) · Priority: low · Impact: crash · Novelty: new

Finding page: [../../bugs/LFV-1517.html](../../bugs/LFV-1517.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a TermAutomatonQuery for 'a', any term, 'b' (addAnyTransition, or the token '*' through TokenStreamToTermAutomatonQuery); searching with it returns the hit, and you then highlight the hit with UnifiedHighlighter, register the query in a Monitor or collect its terms with QueryVisitor.termCollector, expecting a snippet, a registered query or the terms a and b. Instead the highlighter and the monitor throw NullPointerException and the collector holds a third Term whose bytes are null: visit() hands the visitor every key of the query's term map, the null key that stands for the any-term transition included, where the method it replaced (Weight.extractTerms) skipped that key. The 10.6.0 release fails as branch_11x and main do: visit() does this in every release since 8.1.0, and UnifiedHighlighter fails for every such query since 9.5.0 (before that only for automata that rewrite() keeps).

## Expected vs actual

- Expected: Every collected term has bytes: the collector holds `field:a` and `field:b`.
- Actual: `java.lang.AssertionError: expected the terms a and b only, got a Term with null bytes` at line 41: a third `Term`, with null bytes, was collected. Main, branch_11x and 10.6.0, assertions on and off.

## Files

- [src/org/apache/lucene/sandbox/search/TestTermAutomatonQueryVisitAny.java](src/org/apache/lucene/sandbox/search/TestTermAutomatonQueryVisitAny.java) (JUnit test)

## Run it

- Tested on Lucene main 1cfa86310f76 (a commit of 2026-11-17): reproduced, with assertions on and off.
- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- What each build does: the test fails the same way on Lucene main, on the 11.0 release branch (branch_11x daa54f2e0cd3) and with the 10.6.0 release jars, with and without assertions; the finding's record lists every release from 8.1.0 on. What the test does: it builds a TermAutomatonQuery for a, any term, b with the public builder methods (addTransition, addAnyTransition, addTransition; no index), visits it with QueryVisitor.termCollector and requires that every collected term has bytes. It fails with 'expected the terms a and b only, got a Term with null bytes': the collector holds a third Term, with null bytes, for the any-term transition. The NullPointerException that UnifiedHighlighter.highlight and Monitor.register throw for such a query is not in this test, because those classes are in modules the sandbox module does not depend on; the finding page has those runs. The test has no index and no randomness, so the outcome does not depend on the seed. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put the six 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-facet, lucene-queries, lucene-sandbox) in place of the six $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/sandbox.
- This finding was re-run on Lucene main 1a59f96fc6de on 2026-11-21: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected the terms a and b only, got a Term with null bytes.
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
     execute --select-class org.apache.lucene.sandbox.search.TestTermAutomatonQueryVisitAny
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestTermAutomatonQueryVisitAny.java](TestTermAutomatonQueryVisitAny.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
