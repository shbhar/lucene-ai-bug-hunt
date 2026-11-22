# On Lucene 7.0.0 to 10.6.0 a merge can put an older doc-values update back over a newer one, with no error: the merge's in-heap carry-over applies to the merged segment an update that a write had already consumed before the merge took that segment's reader (run with one forced schedule on each of the 75 releases, on 10.6.0 with its default overlays setting; branch_11x and main, both unreleased, no longer have that code)

Finding LFV-1565 · Area: index / IndexWriter merge (the in-heap carry-over of doc-values updates into a merged segment: ReadersAndUpdates, ReaderPool, IndexWriter.commitMergedDeletesAndUpdates; the release lines 7.x to 10.x) · Priority: medium · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-1565.html](../../bugs/LFV-1565.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Two threads update the same doc-values field of the same document with IndexWriter.updateNumericDocValue, first to 1 and then to 2, while a merge of that document's segment is starting, and you commit; both calls have returned, the second with the higher sequence number, so you expect 2 in that commit and in the commits after it (the IndexWriter javadoc: a sequence number 'expresses the effective order in which each change was applied'). On Lucene 10.6.0 the commit made before the merge finishes shows 2 and the commit after the merge shows 1, with no exception, with and without assertions; the index stays well-formed (CheckIndex finds that commit clean) and one value is an older one (run with a forced order: the older update shares its packet with a slow delete-by-query and is still being applied when the merge is initialized, and the merge is held until a commit has written both updates). The merge keeps an in-heap list of updates to carry over to the merged segment, which starts when the merge is initialized while each segment's reader is taken later (Shai Erera's comment of 2013 in getReaderForMerge, the method that gives a merge its segment reader, named a re-application through such a gap as a hazard, and the code of that time took the mark and the reader in one step; the change of 2017 for LUCENE-7888 separated them; Will Zhang asked on the dev list on 2022-08-30, without a reply, whether the two calls should be atomic), so an update that entered the list and was consumed by a write before the reader was taken is applied again, over the newer one: the same order of events, in a main() form, fails on each of the 75 releases from 7.0.0 to 10.6.0 that were run, while branch_11x and main (both unreleased) pass the test and no longer have the list.

## Expected vs actual

- Expected: `n` is 2 in both commits: the update with the higher sequence number decides the value.
- Actual: On 10.6.0 the commit made while the merge is held shows 2 and the commit made after the merge shows 1: `java.lang.AssertionError: expected:<2> but was:<1>` (line 137). Lucene throws nothing.

## Files

- [src/org/apache/lucene/index/TestNewerDocValuesUpdateSurvivesMerge.java](src/org/apache/lucene/index/TestNewerDocValuesUpdateSurvivesMerge.java) (JUnit test)

## Run it

- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): the test passes, with assertions on and off: main no longer has the merge's in-heap list of doc-values updates.
- Tested on Lucene branch_11x 75eea467ac52 (a commit of 2026-11-19): the test passes, with assertions on and off (11.0.0-SNAPSHOT): the 11.0 branch no longer has that list either.
- This finding is on released lines only (the releases and their maintenance branches). The test fails with the jars of the 10.6.0 release and passes on Lucene main and on the 11.0 release branch (branch_11x), which no longer have the merge's in-heap list of doc-values updates (removed by #16570, merged 2026-09-01). The commands below run the test first on the 10.6.0 release jars, where it fails, and then, as the control, on a build of main or of branch_11x, where it passes. For the first run put lucene-core-10.6.0.jar, lucene-codecs-10.6.0.jar and lucene-test-framework-10.6.0.jar (Maven Central, org.apache.lucene) into your copy of this folder, next to the four test jars that the commands name; with V=10.5.2 or V=9.12.3 and the jars of that release the test fails the same way. It does not compile against the 9.0.0 and 8.11.4 jars (the test framework's classes are in other packages there), and four older releases (6.6.6, 7.0.0, 7.7.3 and 8.11.4) were run with adapted forms of it that this folder does not hold. For a run without assertions put -da -dsa -Dtests.asserts=false in place of -ea -esa: the outcomes are the same. What the test does: two threads call IndexWriter.updateNumericDocValue for the field n of one document, first with 1 and then with 2, and the test requires that the second call returned the higher sequence number. The test holds the first update's packet in the rewrite of a delete-by-query of its own and holds the merge of the document's segment in its merge scheduler's wrapForMerge, commits while the merge is held, lets the merge finish, commits again and reads n from each commit. It expects 2 both times; on 10.6.0 the commit made after the merge shows 1 and the test fails with java.lang.AssertionError: expected:&lt;2&gt; but was:&lt;1&gt; (line 137). For the Gradle alternative at the end of this page the module is lucene/core and, to see the failure, the checkout must be one that has the code (run on the tag releases/lucene/10.6.0: 1 test, 1 failure, with assertions on and off); in a checkout of main or of branch_11x the test passes.
- This finding was re-run on Lucene main 1a59f96fc6de on 2026-11-21: the test passed, as recorded before (a control run).
- How to read the outcome: on the 10.6.0 release jars a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;2&gt; but was:&lt;1&gt; at line 137 of the test, the read of the commit made after the merge; on a build of main or of branch_11x the same test passes (1 test successful), which is what the finding states for the unreleased lines.

```bash
# needs JDK 25 on the PATH; run in a copy of this folder that also holds the jars named below (Maven Central)
V=10.6.0   # the release under test
T=randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies
T=$T:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher
CP=lucene-core-$V.jar:lucene-codecs-$V.jar:lucene-test-framework-$V.jar:$T   # org.apache.lucene, release $V

javac -d classes -cp "$CP" $(find src -name '*.java')

# the finding: on the release jars the test fails (1 test failed, expected:<2> but was:<1>)
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE68 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestNewerDocValuesUpdateSurvivesMerge

# the control: a Lucene checkout of main or of branch_11x, built with: ./gradlew --no-daemon assemble
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT   # on branch_11x: V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar:$T

javac -d classes-unreleased -cp "$CP" $(find src -name '*.java')

# there the same test passes (1 test successful)
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE68 \
     -cp "classes-unreleased:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestNewerDocValuesUpdateSurvivesMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestNewerDocValuesUpdateSurvivesMerge.java](TestNewerDocValuesUpdateSurvivesMerge.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
