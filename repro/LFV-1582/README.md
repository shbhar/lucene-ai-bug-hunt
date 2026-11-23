# The javadoc of ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS still gives the default maxThreadCount as max(1, min(4, cpuCoreCount/2)); since 10.0.0 (#13294) the code sets max(1, cpuCoreCount/2), so with an untouched IndexWriterConfig a core count of 9 gives the documented limits (maxThreadCount 4, maxMergeCount 9) and a core count of 10 gives 5 and 10 (run on the 10.0.0 and 10.6.0 release jars, branch_11x and main; documentation defect, the code is the intended side)

Finding LFV-1582 · Area: index / ConcurrentMergeScheduler default merge limits (javadoc of the public constant AUTO_DETECT_MERGES_AND_THREADS) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1582.html](../../bugs/LFV-1582.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The javadoc of ConcurrentMergeScheduler.AUTO_DETECT_MERGES_AND_THREADS, the automatic setting that an untouched IndexWriterConfig uses, says that the default maxThreadCount is max(1, min(4, cpuCoreCount/2)) and maxMergeCount is maxThreadCount + 5, so a reader expects at most 4 and 9. Since 10.0.0 the code sets max(1, cpuCoreCount/2), without the cap of four (#13294; deliberate, and stated in the 10.0.0 change list): on a JVM that reports 10 processors the scheduler's getters return 5 and 10 once an IndexWriter has been opened with the config, and on one that reports 64 they return 32 and 37, while at 9 they return the documented 4 and 9 (run on the 10.6.0 release jars, branch_11x 262e019500ec and main b5a4546a5964). Nothing runs wrong and the code is the intended side: the defect is the stale javadoc sentence. Lucene's own test of the default (TestConcurrentMergeScheduler.testNonSpinningDefaults, a LuceneTestCase) asserts threadCount &lt;= 4, and for a LuceneTestCase suite the test framework sets the core count that the scheduler reads to a value from 1 to 4, where that bound holds with and without the cap.

## Expected vs actual

- Expected: `getMaxThreadCount()` returns 4, the number that the javadoc's formula `max(1, min(4, cpuCoreCount/2))` gives at the test's core count of 10.
- Actual: `getMaxThreadCount()` returns 5, the code's `max(1, 10/2)`: `java.lang.AssertionError: expected:<4> but was:<5>`. The 5 is intended; the stale side is the javadoc sentence.

## Files

- [src/org/example/cms/TestCmsAutoDetectJavadoc.java](src/org/example/cms/TestCmsAutoDetectJavadoc.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): the test fails as the note below describes, with assertions on and off.
- Tested on Lucene branch_11x 262e019500ec (a commit of 2026-11-22): the test fails the same way, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene branch_11x 8e4ee3d41386 (a commit of 2026-11-23): the test fails the same way, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): the test fails the same way, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (262e019500ec) and on Lucene main, with and without assertions; it also fails with the release jars of each of the twelve releases 10.0.0 to 10.5.2. No code is wrong here: the finding is one javadoc sentence, and the half-the-processors default of the code is deliberate and stated in the 10.0.0 change list. What the test asserts is the documented formula, not a behaviour of the scheduler that is wrong: it sets the scheduler's own core-count system property (ConcurrentMergeScheduler.DEFAULT_CPU_CORE_COUNT_PROPERTY, 'lucene.cms.override_core_count') to 10 inside the test, opens an IndexWriter with an untouched IndexWriterConfig over a ByteBuffersDirectory, compares the scheduler's getMaxThreadCount() with 4, the value of the javadoc's max(1, min(4, cpuCoreCount/2)) at a core count of 10, and clears the property again. The getter returns 5, the value of the code's max(1, cpuCoreCount/2): java.lang.AssertionError: expected:&lt;4&gt; but was:&lt;5&gt;. Control: with the 9.12.3 release jars, whose code still has the cap of four, the same block reports 1 test successful, with and without assertions. The two formulas give the same number up to a core count of 9 (at 9 both give 4) and differ from 10 on: a probe program printed the documented limits at the core counts 1, 2, 3, 4, 8 and 9 and other limits at 10, 11, 16 and 64 on main b5a4546a5964, branch_11x 262e019500ec, 10.6.0 and 10.0.0, and the documented limits at all ten counts on 9.12.3; an SMT model of the two formulas (Z3, 32-bit integers) gives 10 as the smallest core count at which they differ. The test is a plain JUnit 4 class, not a LuceneTestCase, and draws nothing from the seed. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for a release, set V to its version (the thirteen releases 10.0.0 to 10.6.0 and 9.12.3 were run) and use the three jars of that release from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework) in place of the three $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/core: with the test copied to lucene/core/src/test/org/example/cms/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 262e019500ec and on main b5a4546a5964, with -Ptests.asserts=true and with -Ptests.asserts=false.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: still reproduces.
- How to read the outcome: a failing test shows the stale javadoc sentence, not a wrong behaviour of the code: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;4&gt; but was:&lt;5&gt; (4 is the javadoc's number at a core count of 10, 5 is what the scheduler's getter returns); with the 9.12.3 release jars, whose code has the cap of four, the same run reports 1 test successful.
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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.example.cms.TestCmsAutoDetectJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestCmsAutoDetectJavadoc.java](TestCmsAutoDetectJavadoc.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
