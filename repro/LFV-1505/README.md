# On every release from 9.8.0 to 10.6.0, a kNN query on a segment written by Lucene 9.0.0 whose vector field some documents lack returns each hit's graph ordinal as its document id, so the hits are other documents (in the test, a document that has no vector); known upstream (#13910) and fixed only on main and branch_11x: the 9.12.1, 9.12.2 and 9.12.3 releases, which carry the change listed as the fix (#13947), still do it, and no 10.x release has a fix

Finding LFV-1505 · Area: backward-codecs / Lucene90 HNSW vectors reader (kNN search on segments written by Lucene 9.0.0) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

Fixed upstream: [#13910](https://github.com/apache/lucene/pull/13910) (commit [3983fa2c8d82](https://github.com/apache/lucene/commit/3983fa2c8d822fa83206b956d6250fcc57954352)), merged 2024-10-17; also on the 11.0 release branch (the same commit). On released lines only (9.8.0 to 10.6.0): main already had the fix when the finding was listed.

## What it shows

You open an index whose segment was written by Lucene 9.0.0 and in which only some documents have a vector (two documents, only document 1 has one) and run KnnFloatVectorQuery for that vector with k = 1. You expect document 1. Instead, on 9.8.0 through 10.6.0 the reader hands the hit's graph ordinal (0) to the collector as its document id, so the query returns document 0, which has no vector, with the right score and no error; 9.7.0 and earlier, branch_11x and main all return document 1. The defect is known upstream (#13910) and fixed on main and branch_11x: this is not a new bug.

## Expected vs actual

- Expected: You expect document 1.
- Actual: On 9.8.0 through 10.6.0 the reader hands the hit's graph ordinal (0) to the collector as its document id, so the query returns document 0, which has no vector, with the right score and no error; 9.7.0 and earlier, branch_11x and main all return document 1.

## Files

- [src/org/apache/lucene/backward_codecs/lucene90/TestLucene90SparseVectorFieldKnn.java](src/org/apache/lucene/backward_codecs/lucene90/TestLucene90SparseVectorFieldKnn.java) (JUnit test)

## Run it

- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0), and on a build of this commit.
- This test fails only on the released lines: every release from 9.8.0 to 10.6.0. It passes on Lucene main 9965d34e3c77 (2026-11-16) and on branch_11x daa54f2e0cd3, with and without assertions, because the defect is fixed there; a build of the current main or of the 11.0 branch is therefore the wrong target. Run it on 10.6.0: either build a checkout of the tag releases/lucene/10.6.0 as below, or put the four released jars of 10.6.0 from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-backward-codecs) in place of the four $LUCENE jars. What the test does: it unpacks a two-document index that the 9.0.0 release wrote (embedded in the source as a base64 zip; document 0 has no vector, document 1 has the vector [1, 0]), opens it with the default reader and searcher and runs KnnFloatVectorQuery for [1, 0] with k = 1. It expects document 1 and fails with 'expected:&lt;1&gt; but was:&lt;0&gt;'. It uses no randomness.
- This finding was re-run on Lucene main b5a4546a5964 on 2026-11-22: the test passed, as recorded before (a control run); in the commands below, V=12.0.0-SNAPSHOT on a build of main b5a4546a5964.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=10.6.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.backward_codecs.lucene90.TestLucene90SparseVectorFieldKnn
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
