# FilterMergePolicy.getSizeUnit() returns BYTES instead of the wrapped policy's unit, so with a wrapped LogDocMergePolicy IndexWriter decides compound files for merged segments against the byte threshold and forceMerge(1) can rewrite an already merged segment every time

Finding LFV-179 · Area: index / merge policies (FilterMergePolicy size unit, compound-file threshold) · Priority: low · Impact: minor · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-179.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-179.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a LogDocMergePolicy in a FilterMergePolicy (or in SoftDeletesRetentionMergePolicy, UpgradeIndexMergePolicy or OneMergeWrappingMergePolicy) and expect the wrapper to make the same compound-file choices as the policy it wraps, as it did in 10.x. Instead, on main FilterMergePolicy.size() still returns the wrapped policy's document count while getSizeUnit() says BYTES, so the compound-file check compares a document count with the 64 MiB byte threshold: under FilterMergePolicy(LogDocMergePolicy), forceMerge(1) of 70,000 small documents writes a compound segment where the plain policy writes a non-compound one, and every later forceMerge(1) rewrites that already merged segment again (3 of 3 calls).

## Expected vs actual

- Expected: The second `forceMerge(1)` leaves segment `_1` alone; with the plain `LogDocMergePolicy`, `_1` is not a compound file.
- Actual: `ComparisonFailure: second forceMerge(1) rewrote _1 (compound file: true) expected:<_[1]> but was:<_[2]>`

## Files

- [src/org/apache/lucene/index/TestFilterMergePolicySizeUnit.java](src/org/apache/lucene/index/TestFilterMergePolicySizeUnit.java) (JUnit test)
- [src/org/apache/lucene/index/TestWrappedLogDocMergePolicyCompoundFile.java](src/org/apache/lucene/index/TestWrappedLogDocMergePolicyCompoundFile.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- Runs 1 and 2 use the two original tests under src/: on main 5adff78bb168 TestFilterMergePolicySizeUnit fails 2 of 2 (the wrapper reports BYTES where DOCS is expected) and TestWrappedLogDocMergePolicyCompoundFile fails 1 of 1 (the plain and the wrapped policy make different compound-file decisions for the same merge), with assertions on and off. The expected and actual lines above are those of the finding page's one-test copy at the top of this folder, TestFilterMergePolicyForceMergeNoOp.java; copy it into src/org/apache/lucene/index/ and select org.apache.lucene.index.TestFilterMergePolicyForceMergeNoOp in run 1 to run it. On the 11.0 branch (branch_11x daa54f2e0cd3, V=11.0.0-SNAPSHOT) and on main 781025d6fe32, where #16962 (2026-11-03) made the no-argument MatchAllDocsQuery constructor private, the javac line stops at line 43 of TestFilterMergePolicySizeUnit.java: change MatchAllDocsQuery::new there to a lambda that returns MatchAllDocsQuery.INSTANCE, () -&gt; MatchAllDocsQuery.INSTANCE, and the two tests fail 2 of 2 and 1 of 1 as described, with assertions on and off (or leave that file out: the one-test copy and TestWrappedLogDocMergePolicyCompoundFile then compile unchanged and fail 1 of 1 each, and the one-test copy alone passes on the 10.6.0 release jars).
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFilterMergePolicySizeUnit

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestWrappedLogDocMergePolicyCompoundFile
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestFilterMergePolicyForceMergeNoOp.java](TestFilterMergePolicyForceMergeNoOp.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
