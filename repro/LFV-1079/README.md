# Function-query vector similarity value sources (Float/Byte/Float16VectorSimilarityFunction) leave the similarity function out of equals but hash it, so EUCLIDEAN and COSINE sources over the same vectors compare equal with different hash codes

Finding LFV-1079 · Area: queries / function queries: valuesource.VectorSimilarityFunction equality (base of Float/Byte/Float16VectorSimilarityFunction, and so of the FunctionQuery, FunctionRangeQuery and FunctionScoreQuery that wrap them) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1079.html](../../bugs/LFV-1079.html)

AI-assisted finding, confirmed by automated checks. Upstream status: Fixed, by Simranjeet Singh (sjs004; PR #16974, which cites this list; approved and merged by Michael Sokolov on 2026-11-23) on 2026-11-23 (https://github.com/apache/lucene/pull/16974). Simranjeet Singh (sjs004) fixed it in PR #16974 (opened 2026-11-03), which cites this row ("Resolves LFV-1079" of the tracking issue #16937): VectorSimilarityFunction#equals now also compares the similarity function, the same one condition as this row's fix, and three new tests check it for the Float, Byte and Float16 sources. The owner (shbhar) approved the pull request on 2026-11-03; Michael Sokolov approved it on 2026-11-23 and merged it into main the same day as c456c8f5c3ad (no milestone). The two files of the merged commit are those of the pull request's one commit of 2026-11-03. Not on branch_11x (8e4ee3d41386 and, later that day, 784a2d5d03d4) or branch_10_6 (451a69c1d4a6) when read on 2026-11-23, so the 11.0 and 10.6 lines keep the bug as of that day. From our runs of 2026-11-23, each with and without assertions: the minimal repro passes on main c456c8f5c3ad and 11c8b6ea99ae and fails on 3cee9ed259e9 (the commit before the fix), on branch_11x 8e4ee3d41386 and on the 10.6.0 release; with the class from before the fix put back on main 11c8b6ea99ae it fails again, and the three new tests fail without the source change. Novelty stays new: the PR comes from this list. This site files nothing upstream.

Fixed upstream: [#16974](https://github.com/apache/lucene/pull/16974) (commit [c456c8f5c3ad](https://github.com/apache/lucene/commit/c456c8f5c3ad58bc8a8922e473de931c39993883)), merged 2026-11-23.

## What it shows

You build two function-query value sources over the same vector field and the same query vector, one with EUCLIDEAN and one with COSINE (new FloatVectorSimilarityFunction(EUCLIDEAN, field, target) and the same with COSINE), and expect them to be unequal because they compute different scores; the class itself treats the function as part of its identity, since hashCode() and description() include it. Instead equals() compares only the two vector sources and returns true, while hashCode() returns different values, which breaks the java.lang.Object rule that equal objects have equal hash codes. The Byte and Float16 subclasses share this equals, and FunctionQuery and the other wrappers pass the false equality on.

## Expected vs actual

- Expected: The EUCLIDEAN and the COSINE source are not equal, so `assertNotEquals` passes.
- Actual: `equals()` returns true: `AssertionError: Values should be different. Actual: COSINE(FloatKnnVectorFieldSource(v), ConstKnnFloatValueSource([1.0, 2.0]))`, with assertions on and off.

## Files

- [src/org/apache/lucene/queries/function/TestVectorSimilarityFunctionEquals.java](src/org/apache/lucene/queries/function/TestVectorSimilarityFunctionEquals.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (a commit of 2026-11-02): reproduced.
- Upstream fixed this finding on main on 2026-11-23: #16974 was merged as c456c8f5c3ad (see Fixed upstream above). On a build of that commit the test passes; on a build of main 3cee9ed259e9, the commit before it, the test fails (runs of 2026-11-23). To see the finding, run the commands on a build that has it. As printed (V=12.0.0-SNAPSHOT) they fit the tested commit above: run as printed on a build of main e1faeba08bde on 2026-11-23, the test fails. The fix was not on branch_11x on 2026-11-23: on builds of branch_11x 8e4ee3d41386 and 784a2d5d03d4 the test fails (set V=11.0.0-SNAPSHOT). Without a build: put lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar, lucene-test-framework-10.6.0.jar and lucene-queries-10.6.0.jar (Maven Central, org.apache.lucene) in place of the four $LUCENE jars and set V=10.6.0; the 10.6.0 release fails the test. Each of these runs gave the same result with and without assertions.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: it no longer reproduces there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestVectorSimilarityFunctionEquals
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestVectorSimilarityFunctionEquals.java](TestVectorSimilarityFunctionEquals.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
