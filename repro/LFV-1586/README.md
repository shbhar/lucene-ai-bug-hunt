# The class javadoc of IndexWriter says of the merge policy 'The default is LogByteSizeMergePolicy', and the javadoc of LogMergePolicy#setMaxMergeDocs calls LogByteSizeMergePolicy 'the default merge policy'; a writer opened with an untouched IndexWriterConfig has a TieredMergePolicy (LUCENE-1076: from 3.2.0 for a config built with Version.LUCENE_32 or later, without that condition from 4.0.0; run on 28 releases from 3.2.0 to 10.6.0, on branch_11x and on main; documentation defect, the code is the intended side)

Finding LFV-1586 · Area: index / the default merge policy as two javadoc sentences state it (the class javadoc of IndexWriter; LogMergePolicy#setMaxMergeDocs) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The class javadoc of IndexWriter says about the merge policy 'The default is LogByteSizeMergePolicy', and the javadoc of LogMergePolicy#setMaxMergeDocs speaks of 'The default merge policy (LogByteSizeMergePolicy)'. You open an IndexWriter with an IndexWriterConfig whose merge policy you did not set and you expect, by the two javadoc sentences, a LogByteSizeMergePolicy. Instead the writer has a TieredMergePolicy (run on branch_11x 262e019500ec, main b5a4546a5964 and the 10.6.0 release jars, with and without assertions). Nothing runs wrong and the code is the intended side: TieredMergePolicy became the default on purpose (LUCENE-1076, in the 3.2.0 change list), and the javadoc of MergePolicy, of TieredMergePolicy, of both IndexWriterConfig constructors and of IndexWriter#forceMergeDeletes names it; the two sentences were true before that change and were left behind by it. A caller who goes by them can meet a ClassCastException when casting the config's default policy to LogByteSizeMergePolicy (to reach setMaxMergeMB), and can find documents out of insertion order after merges (run with 24 one-document segments of unequal size and a serial merge scheduler, where LogByteSizeMergePolicy kept the order), which is announced behaviour of TieredMergePolicy.

## Expected vs actual

- Expected: You expect, by the two javadoc sentences, a LogByteSizeMergePolicy.
- Actual: The writer has a TieredMergePolicy (run on branch_11x 262e019500ec, main b5a4546a5964 and the 10.6.0 release jars, with and without assertions).

## Files

- [src/org/example/index/TestDefaultMergePolicyJavadoc.java](src/org/example/index/TestDefaultMergePolicyJavadoc.java) (JUnit test)

## Run it

- Tested on Lucene main 3cee9ed259e9 (a commit of 2026-11-23): the test fails as the note below describes, with assertions on and off.
- Tested on Lucene branch_11x 8e4ee3d41386 (a commit of 2026-11-23): the test fails the same way, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): the test fails the same way, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (8e4ee3d41386) and on Lucene main, with and without assertions. No code is wrong here: the finding is two javadoc sentences, 'The default is LogByteSizeMergePolicy' in the class javadoc of IndexWriter (IndexWriter.java:159) and 'The default merge policy (LogByteSizeMergePolicy)' on LogMergePolicy#setMaxMergeDocs (LogMergePolicy.java:697), as read at main 3cee9ed259e9; TieredMergePolicy as the default is deliberate, was announced in the 3.2.0 change list (LUCENE-1076) and is named by the javadoc of both IndexWriterConfig constructors, of MergePolicy and of TieredMergePolicy. Known outside Lucene in part: the first of the two sentences is named as stale documentation in a research note in a public outside repository, as read on 2026-11-23; the second sentence was named nowhere in the searches made. What the test asserts is the documented default, not a behaviour of the code that is wrong: it compares the class of the merge policy of an untouched new IndexWriterConfig() with LogByteSizeMergePolicy.class. Expected, going by the two javadoc sentences: LogByteSizeMergePolicy. Actual: TieredMergePolicy: java.lang.AssertionError: expected:&lt;class org.apache.lucene.index.LogByteSizeMergePolicy&gt; but was:&lt;class org.apache.lucene.index.TieredMergePolicy&gt;. Which test was run where: the file of this folder, TestDefaultMergePolicyJavadoc, a plain JUnit 4 class with one method that opens no writer, was run on main 3cee9ed259e9, on branch_11x 8e4ee3d41386 and with the 10.6.0 release jars. The 28 releases from 3.2.0 to 10.6.0 that the text above names were run with a first form of the test that is not shipped here: it opens an IndexWriter over the untouched config and compares the class of the writer's merge policy, and its message opens with 'default merge policy'. That form ran unchanged on 7.5.0 and later, and in four copies that differ in how the config and the directory are built and not in the assertion: one for the 3.x line (run on 3.2.0 and 3.6.2: the config takes a version constant and an analyzer, and the copy passes the release's latest version constant), one for 4.0.0 to 4.10.4 (the same, with the analyzer of the analyzers-common module), one for 5.0.0 to 6.1.0 (the config takes an analyzer and no version constant) and one for 6.2.0 to 7.4.0 (RAMDirectory in place of ByteBuffersDirectory). On 3.2.0 and 3.6.2 a config built with Version.LUCENE_30 or LUCENE_31 kept LogByteSizeMergePolicy, and the 3.1.0 release passes the copy for the 3.x line. The test is a plain JUnit 4 class, not a LuceneTestCase, and draws nothing from the seed. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for the release, set V=10.6.0 and use the three 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework) in place of the three $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/core: with the test copied to lucene/core/src/test/org/example/index/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 8e4ee3d41386 and on main 3cee9ed259e9, with -Ptests.asserts=true and with -Ptests.asserts=false.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: still reproduces.
- How to read the outcome: a failing test shows the two stale javadoc sentences, not a wrong behaviour of the code: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;class org.apache.lucene.index.LogByteSizeMergePolicy&gt; but was:&lt;class org.apache.lucene.index.TieredMergePolicy&gt; (LogByteSizeMergePolicy is the default that the two sentences name, TieredMergePolicy is the merge policy of an untouched IndexWriterConfig).
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
     execute --select-class org.example.index.TestDefaultMergePolicyJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
