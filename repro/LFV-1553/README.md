# Test suite on branch_11x and main (both unreleased): the two testDictionaryReuse tests added by #14397 fail for some seeds, because they assert one segment on a writer made with newIndexWriterConfig(), which for some seeds sets maxBufferedDocs so low (2..6 for the seven documents of the stored-fields test, 2..5 for the six of the term-vectors test) that the documents are flushed into several segments; the failures do not come from the code under test

Finding LFV-1553 · Area: test sources / core codec tests: the dictionary-reuse tests of the compressing stored-fields and term-vectors formats (LZ4 with a preset dictionary) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1553.html](../../bugs/LFV-1553.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's own tests on branch_11x (to become 11.0.0) or on main and expect TestCompressingStoredFieldsFormat.testDictionaryReuse and TestCompressingTermVectorsFormat.testDictionaryReuse, the two tests added with #14397, to pass unless the code they test is wrong. Instead they fail for some seeds with 'java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;2&gt;' (in other runs 3, and 4 for the stored-fields test), for example both with the master seed 8621B64601478ADE: each asserts that its seven or six documents are one segment, on a writer made with newIndexWriterConfig() and NoMergePolicy, and for some seeds that helper of the test framework sets maxBufferedDocs to a value of 2..6 (2..5 for the six documents), so the writer flushes several segments and nothing merges them. It is a false alarm of the two tests, not a wrong stored value or term vector: the assertion fails before the test reads a stored value or term vector, and the same seeds pass once the tests pin maxBufferedDocs (run with .setMaxBufferedDocs(100) on the two writer configs); the tests are on branch_11x (to become 11.0.0; since the backport 15b6826d849f of 2026-11-19) and on main, both unreleased, and in no release; on a build of branch_11x 5d7ca54ba503 the same master seed fails both tests, with assertions on and off.

## Expected vs actual

- Expected: One segment: `assertEquals(1, reader.leaves().size())` holds, as both `testDictionaryReuse` tests assert.
- Actual: `java.lang.AssertionError: expected:<1> but was:<2>` for the one test of this page with its seed `7D797B773AD37D33`; Lucene's own two tests with the seed `8621B64601478ADE`: `but was:<3>` (stored fields) and `but was:<2>` (term vectors).

## Files

- [src/org/apache/lucene/codecs/lucene90/compressing/TestSevenDocumentsAreOneSegment.java](src/org/apache/lucene/codecs/lucene90/compressing/TestSevenDocumentsAreOneSegment.java) (JUnit test)

## Run it

- Tested on Lucene main 1a59f96fc6de (a commit of 2026-11-21): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 3221dd4cf3a1 (a commit of 2026-11-20): reproduced, with assertions on and off (set V=11.0.0-SNAPSHOT).
- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 75eea467ac52 (a commit of 2026-11-19): reproduced, with assertions on and off (set V=11.0.0-SNAPSHOT).
- Tested on Lucene main f6e694652c3d (a commit of 2026-11-19): reproduced, with assertions on and off.
- What the test is: the writer set-up of Lucene's two testDictionaryReuse tests (newIndexWriterConfig() with NoMergePolicy, without their codec line) and their assertion, with seven empty documents. With the seed 7D797B773AD37D33 the seven documents are flushed into two segments on the builds named above, and the test fails with 'expected:&lt;1&gt; but was:&lt;2&gt;'. The seed is this test's own (Lucene's own two tests pass with it) and is bound to the order in which a build's test framework draws its random values, so this test does not show which builds have the two tests: with the same seed it also fails on main 2dcbaebd8fd6 and on branch_11x b98f116b3179, two commits from before #14397 reached each branch, and it passes with the 10.6.0 release jars (runs of 2026-11-19, with assertions on and off; for the release, the three 10.6.0 jars from Maven Central, org.apache.lucene: lucene-core, lucene-codecs and lucene-test-framework, stand in place of the three $LUCENE jars). Lucene's own two tests are run in a checkout of main or of branch_11x: ./gradlew :lucene:core:test --tests org.apache.lucene.codecs.lucene90.compressing.TestCompressingStoredFieldsFormat.testDictionaryReuse --tests org.apache.lucene.codecs.lucene90.compressing.TestCompressingTermVectorsFormat.testDictionaryReuse -Ptests.seed=8621B64601478ADE fails both, 'expected:&lt;1&gt; but was:&lt;3&gt;' for the stored-fields test and 'expected:&lt;1&gt; but was:&lt;2&gt;' for the term-vectors test (run on 2026-11-20 on main f2b061406c4b and on branch_11x 75eea467ac52, also with -Ptests.asserts=false). On a checkout of branch_11x set V=11.0.0-SNAPSHOT in the commands below.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: still reproduces.
- How to read the outcome: the test of this folder fails with java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;2&gt; where the set-up of the two tests gives two segments; whether a build has the finding is read from Lucene's own two tests, as the note above says.
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

# run 1: assertions on, seed 7D797B773AD37D33
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=7D797B773AD37D33 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.compressing.TestSevenDocumentsAreOneSegment
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSevenDocumentsAreOneSegment.java](TestSevenDocumentsAreOneSegment.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
