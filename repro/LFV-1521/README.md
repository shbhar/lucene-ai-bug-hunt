# Since 9.11.0 the exact (brute-force) part of a filtered kNN query over an ExitableDirectoryReader never checks the reader's QueryTimeout: the wrapper's vector values hand out scorer(target) unchecked, so a scan that 9.2.0 to 9.10.0 stopped runs to the end of the segment (9.11.0 through 10.6.0, branch_11x and main)

Finding LFV-1521 · Area: index / ExitableDirectoryReader (vector values) and search / kNN queries (exact scan) · Priority: low · Impact: minor · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1521.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1521.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a reader with ExitableDirectoryReader and a QueryTimeout and run a kNN query with a filter that makes it score a segment's accepted vectors one by one (the filter accepts no more documents than the query's per-segment k, or the approximate search was cut short or found too few hits), and the time limit runs out as that scan starts. You expect the scan to stop with ExitingReaderException at the wrapper's next check, as it did on 9.2.0 to 9.10.0 (in today's code the wrapper's own vector iterator checks the limit each time the document ID has moved on by 1,000). Instead the scan never asks the reader's limit: since 9.11.0 the wrapper returns the wrapped values' own scorer from scorer(target) and the exact search scores through that scorer, so all 2,000 vectors of the test are scored and the search returns as if no limit were set. Not new in 11: the 10.6.0 release behaves as branch_11x daa54f2e0cd3 does; nothing is wrong in the results and the scan ends.

## Expected vs actual

- Expected: The search throws `ExitingReaderException`, as it does on 9.10.0 (two-argument copy of the test).
- Actual: No exception, the search returns: `Expected exception ExitingReaderException but no exception was thrown` at line 66.

## Files

- [src/org/apache/lucene/index/TestExitableReaderExactKnnSearch.java](src/org/apache/lucene/index/TestExitableReaderExactKnnSearch.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 9d2b5a15427a (a commit of 2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- What each build does: the test fails the same way on Lucene main, on branch_11x 9d2b5a15427a and with the 10.6.0 release jars, with and without assertions; the finding's record lists every release from 9.11.0 on. It is a late stop, not a wrong answer: the hits of the search are those of an unwrapped reader. What the test does: it writes 2,000 documents with the one-dimensional vectors (0) to (1999) and the term color:red into one segment, wraps the reader with ExitableDirectoryReader.wrap and a QueryTimeout that reads a flag, and runs a KnnFloatVectorQuery with the filter color:red and k = 2,000, so that the query scores the 2,000 accepted vectors one by one (its exact scan). It overrides the protected exactSearch only to set the flag as that scan starts, and expects the search to throw ExitingReaderException. It fails with 'Expected exception ExitingReaderException but no exception was thrown': the scan does not ask the reader's limit, and the search returns. The test's input has no randomness; the test framework draws the codec from the seed, and the message was the same under every seed that was run. On a checkout of branch_11x at 9d2b5a15427a, which builds 11.0.0-SNAPSHOT, set V=11.0.0-SNAPSHOT; for the release, put the three 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework) in place of the three $LUCENE jars. Releases before 9.11.0 need another copy of the test (the overridden method had two parameters then), which is not in this folder. For the Gradle alternative at the end of this page the module is lucene/core and the test's place is lucene/core/src/test/org/apache/lucene/index/.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with junit.framework.AssertionFailedError: Expected exception ExitingReaderException but no exception was thrown (the search returned although the reader's limit was up).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 0A7E0818
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=0A7E0818 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestExitableReaderExactKnnSearch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestExitableReaderExactKnnSearch.java](TestExitableReaderExactKnnSearch.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
