# The 'Example usage' blocks in the class javadoc of AllGroupHeadsCollectorManager, DocValuesStatsCollectorManager and LargeNumHitsTopDocsCollectorManager call new MatchAllDocsQuery(), whose constructor #16962 made private on branch_11x and main: with the elided searcher supplied the examples compile and run on 10.6.0 and are rejected by javac on branch_11x 3221dd4cf3a1 and main 7b8418faa2ff (documentation defect; the private constructor is deliberate)

Finding LFV-1576 · Area: grouping, misc and sandbox / collector managers: the 'Example usage' blocks of three class javadocs, which call core's MatchAllDocsQuery · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1576.html](../../bugs/LFV-1576.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the class javadoc of AllGroupHeadsCollectorManager (grouping), DocValuesStatsCollectorManager (misc) or LargeNumHitsTopDocsCollectorManager (sandbox) and take its 'Example usage' block into code built against branch_11x, supplying the searcher that the block's first line elides ('IndexSearcher searcher = ...;'). You expect it to compile, as the same text does on 10.6.0 (where javac only notes a deprecated API). Instead the compiler stops at the example's search call with 'MatchAllDocsQuery() has private access in MatchAllDocsQuery' on branch_11x 3221dd4cf3a1 and main 7b8418faa2ff, because #16962 made the no-argument constructor private on branch_11x and main, on purpose, and left these three javadoc lines as they were. With MatchAllDocsQuery.INSTANCE in that call, the replacement that the 11.0 section of MIGRATE.md names, each example compiles and runs on both builds and on 10.6.0, so the defect is in the three javadoc lines and not in the constructor change.

## Expected vs actual

- Expected: The three blocks compile, as they do against the 10.6.0 release jars, where javac notes a deprecated API (the test passes 1 of 1 there).
- Actual: The test fails 1 of 1, assertions on and off, on builds of branch_11x `262e019500ec` and main `b5a4546a5964`: three javac errors, one per block, each `MatchAllDocsQuery() has private access in org.apache.lucene.search.MatchAllDocsQuery`.

## Files

- [src/TestCollectorManagerJavadocExamples.java](src/TestCollectorManagerJavadocExamples.java) (JUnit test)

## Run it

- Tested on Lucene branch_11x 3221dd4cf3a1 (a commit of 2026-11-20): reproduced, with assertions on and off (11.0.0-SNAPSHOT): the test's javac call rejects the three examples with three errors, each 'MatchAllDocsQuery() has private access'.
- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): reproduced the same way, with assertions on and off (12.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): the test passes, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0): the constructor is public and deprecated there, so this run is the control.
- This is a documentation defect, and nothing wrong runs: the three 'Example usage' blocks are javadoc text, and the private constructor is deliberate (#16962, merged 2026-11-03; listed in CHANGES.txt under 11.0.0, API Changes, and in the 11.0 section of MIGRATE.md, which names MatchAllDocsQuery.INSTANCE as the replacement). What the test does: it hands one Java source to javac (through javax.tools, with the test's own class path and no lint option) and asserts that the source compiles; it compiles only, and none of the three examples is run. The source holds the three blocks of the class javadoc of AllGroupHeadsCollectorManager (lucene/grouping; the class is marked @lucene.experimental), DocValuesStatsCollectorManager (lucene/misc) and LargeNumHitsTopDocsCollectorManager (lucene/sandbox), each in a method whose parameter is the searcher that the block's first line leaves to the reader ('IndexSearcher searcher = ...; // your IndexSearcher'). The commands below run the test first on a build of branch_11x (run at 3221dd4cf3a1), where it fails, and then on the 10.6.0 release jars, where it passes. For a checkout of main set V=12.0.0-SNAPSHOT in the first part (run at 7b8418faa2ff: the test fails the same way). For the second part put lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar, lucene-test-framework-10.6.0.jar, lucene-grouping-10.6.0.jar, lucene-misc-10.6.0.jar and lucene-sandbox-10.6.0.jar (Maven Central, org.apache.lucene) into your copy of this folder, next to the four test jars that the commands name. For a run without assertions put -da -dsa -Dtests.asserts=false in place of -ea -esa: the outcomes are the same. The seed in the commands fixes the locale that Lucene's test framework picks: under another seed the outcome is the same, but javac may word its three errors in another language (seen with -Dtests.locale=ja-JP). The control passes with javac's default options: on 10.6.0 javac compiles the three calls and notes a deprecated API; with javac -Xlint:deprecation -Werror, which turns the deprecation warnings into errors, the same source is rejected on the releases 10.5.0 to 10.6.0 too, because of the deprecation and not of the constructor's access. With MatchAllDocsQuery.INSTANCE in the three calls the same source compiles on both branches and on 10.6.0, also with -Xlint:deprecation -Werror. The test's input has no randomness.
- This finding was re-run on Lucene main 11c8b6ea99ae on 2026-11-23: still reproduces; in the commands below, V=12.0.0-SNAPSHOT on a build of main 11c8b6ea99ae.
- How to read the outcome: on a build of branch_11x or of main a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError and javac's three errors as its message, each 'MatchAllDocsQuery() has private access in org.apache.lucene.search.MatchAllDocsQuery' (at lines 12, 18 and 21 of the compiled source, one per example); on the 10.6.0 release jars the same test passes (1 test successful): that run is the control.

```bash
# needs JDK 25 on the PATH; run in a copy of this folder that also holds the jars named below (Maven Central)
T=randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies
T=$T:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher

# the finding: a Lucene checkout of branch_11x, built with: ./gradlew --no-daemon assemble
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar:$T

javac -d classes -cp "$CP" $(find src -name '*.java')

# there the test fails (1 test failed): javac rejects the three examples, one error each
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestCollectorManagerJavadocExamples

# the control: the 10.6.0 release jars, where the constructor is public and deprecated
V=10.6.0
CP=lucene-core-$V.jar:lucene-codecs-$V.jar:lucene-test-framework-$V.jar   # org.apache.lucene, release $V
CP=$CP:lucene-grouping-$V.jar:lucene-misc-$V.jar:lucene-sandbox-$V.jar:$T

javac -d classes-release -cp "$CP" $(find src -name '*.java')

# there the same test passes (1 test successful)
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-release:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestCollectorManagerJavadocExamples
```

## Files shown on the finding page

- [TestCollectorManagerJavadocExamples.java](TestCollectorManagerJavadocExamples.java)

Formal models and scripts this finding's part of the hunt used: [../../models/javadoc-examples-matchalldocsquery-constructor/](../../models/javadoc-examples-matchalldocsquery-constructor/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
