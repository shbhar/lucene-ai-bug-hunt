# Lucene's Gradle build declares the option tests.gui ('Enables or disables @RequiresGUI tests', on by default on CI builds) but does not pass it to the test JVM, so the option has no effect on the test JVM and the only @RequiresGUI test, the Luke launch test, is skipped with -Ptests.gui=true, with -Dtests.gui=true and on CI builds: in the 10.6.0 sources, and on branch_11x and main once the method has its missing @Test back (LFV-389); test infrastructure only

Finding LFV-1522 · Area: build and test infrastructure / the Gradle test plugin (build options passed to the test JVM) and the @RequiresGUI test group of the distribution tests · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1522.html](../../bugs/LFV-1522.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run Lucene's own distribution tests with the build option tests.gui switched on (-Ptests.gui=true or -Dtests.gui=true, or on a CI build, where it is on by default) and expect the one @RequiresGUI test, the check that the binary distribution's Luke launch script starts, to run: the option's description is 'Enables or disables @RequiresGUI tests', and the release smoke test sets it for that purpose. Instead the build reports that test as skipped and succeeds, because since the build moved to convention plugins (10.6.0 on the 10.x line; main and branch_11x, both unreleased) the option is declared but no longer handed to the test JVM as the system property that the test group reads; from 9.3.0 to 10.5.2 it was. Only Lucene's own build is affected (no jar, API or index), the test passed in every run made for this finding, one added line in the build plugin makes the option work again, and on branch_11x and main the test method also needs its missing @Test back (LFV-389, a separate finding) before it runs.

## Expected vs actual

- Expected: `1 test` and `BUILD SUCCESSFUL`: the test JVM has the system property `tests.gui`.
- Actual: `1 test, 1 failure`: `java.lang.AssertionError: the build did not pass its option tests.gui to the test JVM`, on main, branch_11x and the 10.6.0 tag, assertions on and off.

## Files

- [src/org/apache/lucene/distribution/TestGuiOptionIsForwardedToTestJvm.java](src/org/apache/lucene/distribution/TestGuiOptionIsForwardedToTestJvm.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced through the tree's own Gradle build, with -Ptests.asserts=true and -Ptests.asserts=false; with one added line in the build plugin the test passes.
- Tested on Lucene branch_11x 9d2b5a15427a (a commit of 2026-11-16): reproduced the same way (11.0.0-SNAPSHOT), with both assertion settings; passes with the one added line.
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced the same way in the 10.6.0 source release (the tag releases/lucene/10.6.0), with both assertion settings; passes with the one added line.
- Test infrastructure of Lucene's own build: no jar, API or index is involved. The finding is in how the Gradle build starts the test JVM, so the repro is a run of that build in a Lucene checkout (main, branch_11x or the tag releases/lucene/10.6.0); compiled against the jars and started by a JUnit launcher, the test cannot show it. What the test does: one JUnit 4 method asserts that the test JVM has the system property tests.gui, the property that the @RequiresGUI test group reads. It does not start Luke. Expected: ':lucene:distribution.tests:test (SUCCESS): 1 test' and BUILD SUCCESSFUL. Observed on all three trees: the test fails with java.lang.AssertionError: the build did not pass its option tests.gui to the test JVM, and Gradle prints ':lucene:distribution.tests:test (FAILURE): 1 test, 1 failure' and BUILD FAILED, while the build's own 'Reproduce with' line carries -Ptests.gui=true. The same with -Ptests.asserts=false; without -Ptests.asserts the build of main and of branch_11x draws the assertion setting from its seed, and the build of the 10.6.0 sources runs with assertions on. In the 10.6.0 sources the effect also shows with no file added: ./gradlew :lucene:distribution.tests:test -Ptests.gui=true reports '9 tests, 1 skipped' and BUILD SUCCESSFUL. With one line added after the option's declaration in build-tools/build-infra/src/main/java/org/apache/lucene/gradle/plugins/java/TestsAndRandomizationPlugin.java, optionsInheritedAsProperties.add("tests.gui");, the command below reports 1 test and BUILD SUCCESSFUL on all three trees. The commands need JDK 25 (java) on the PATH and a first run downloads the Gradle distribution and the build's dependencies; remove the copied file from the checkout afterwards. Run on one Linux aarch64 host without a display.
- This finding was re-run on Lucene main f2b061406c4b on 2026-11-20: still reproduces.
- How to read the outcome: the finding reproduces when the gradlew command reports ':lucene:distribution.tests:test (FAILURE): 1 test, 1 failure' with java.lang.AssertionError: the build did not pass its option tests.gui to the test JVM; a build that passes the option reports 1 test and BUILD SUCCESSFUL.

```bash
# needs JDK 25 on the PATH; run in a copy of this folder
LUCENE=/path/to/lucene   # a Lucene checkout of main, of branch_11x or of the tag releases/lucene/10.6.0
cp src/org/apache/lucene/distribution/TestGuiOptionIsForwardedToTestJvm.java $LUCENE/lucene/distribution.tests/src/test/org/apache/lucene/distribution/
(cd $LUCENE && ./gradlew :lucene:distribution.tests:test --tests "*TestGuiOptionIsForwardedToTestJvm*" -Ptests.gui=true -Ptests.asserts=true)   # expected: 1 test, BUILD SUCCESSFUL; observed: 1 test, 1 failure, BUILD FAILED
# the same with -Ptests.asserts=false in place of -Ptests.asserts=true
```

## Files shown on the finding page

- [TestGuiOptionIsForwardedToTestJvm.java](TestGuiOptionIsForwardedToTestJvm.java)

Formal models and scripts this finding's part of the hunt used: [../../models/tests-gui-build-option-not-passed-to-test-jvm/](../../models/tests-gui-build-option-not-passed-to-test-jvm/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
