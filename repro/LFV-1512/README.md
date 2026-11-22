# Main and branch_11x, both unreleased (11.0.0 would be the first release): the generated POM of lucene-test-framework now brings junit-jupiter-engine and nothing that runs JUnit 4 tests, so a Maven project on Surefire 3.5.4 (the default of Maven 3.9.16) runs none of its JUnit 4 tests after the upgrade and its build stays green unless failIfNoTests is set; 10.6.0 runs them, and the 11.0 migration guide names no step for such a project (a release and migration gap in build metadata and the guide; no shipped class is wrong)

Finding LFV-1512 · Area: test-framework / build metadata (the Maven POM generated for lucene-test-framework, since GITHUB#16027) x release notes (lucene/MIGRATE.md 10.x -> 11.0, the JUnit section) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1512.html](../../bugs/LFV-1512.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Your Maven project has lucene-test-framework as a test dependency and JUnit 4 tests that extend LuceneTestCase; you change the Lucene version from 10.6.0 to 11.0.0-SNAPSHOT (a build of the unreleased 11.0 branch) and run mvn test with Maven 3.9.16, which binds Surefire 3.5.4. You expect the tests to run as before (the JUnit section of the 11.0 migration guide lists nothing to do for a project that keeps JUnit 4 tests). Instead Surefire prints 'Tests run: 0' and BUILD SUCCESS: no test is executed, and the build is green unless failIfNoTests is set. The POM that the branch generates for the test framework brings junit-jupiter-engine 6.1.3, so Surefire switches to its JUnit Platform provider, and nothing on the class path runs JUnit 4 tests (Lucene's own build adds junit-vintage-engine for itself; the POM does not carry it). No Lucene class is wrong: adding junit-vintage-engine 6.1.3 to the project, or using Surefire 3.6.0 or 3.6.1, runs the tests again.

## Expected vs actual

- Expected: `Tests run: 1`, as with 10.6.0.
- Actual: `Tests run: 0, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` on the 11.0 branch, on main and on the nightly snapshot, assertions on and off: the test is never started.

## Files

- [consumer/pom.xml](consumer/pom.xml)
- [consumer/src/test/java/TestConsumer.java](consumer/src/test/java/TestConsumer.java) (JUnit test)

## Run it

- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced as a Maven build (Maven 3.9.16, Surefire 3.5.4, JDK 25.0.3): on a build of this commit the project executes 0 of its 1 tests and the build is green, with assertions on and off; with the 10.6.0 release it executes 1 of 1.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced the same way on a build of this commit (12.0.0-SNAPSHOT).
- In no release: 11.0.0 is not released, the 10.6.0 release runs the test, and 11.0.0 would be the first release with it. This repro is a Maven build, not a failing test: consumer/ is a Maven project of two files, pom.xml and src/test/java/TestConsumer.java (one empty JUnit 4 test in a class that extends LuceneTestCase), and the commands below run it twice with Maven 3.9.16 and JDK 25; the POM names Surefire 3.5.4, the version Maven 3.9.16 binds when a project names none. The commands need Maven 3.9.16 (mvn) and JDK 25 on the PATH; they run in a copy of this folder, and LUCENE must be a Lucene checkout of branch_11x at daa54f2e0cd3. Expected, both times: 'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0' and BUILD SUCCESS. Observed with 10.6.0 from Maven Central: that (Surefire reports its provider JUnit4Provider). Observed with a build of the 11.0 branch: 'Tests run: 0, Failures: 0, Errors: 0, Skipped: 0' and BUILD SUCCESS (provider JUnitPlatformProvider): the test is not executed and nothing fails. The finding is that second output. Compiled against the jars and run with the JUnit Platform console launcher, which has its own engine for JUnit 4 tests, the class passes on the build of the 11.0 branch too. The 11.0 artifacts come from a build of the branch: the three Gradle tasks publish lucene-test-framework, lucene-core and lucene-codecs as 11.0.0-SNAPSHOT into build/maven-artifacts of the checkout, and settings.xml points the POM's snapshot repository (id apache.snapshots) at that directory. Without -s settings.xml the second mvn command takes the project's nightly 11.0.0-SNAPSHOT from Apache's snapshot repository instead, which changes with the branch (on 2026-11-15, build 968 of revision 57f96b20d32b, it gave the same 'Tests run: 0' and BUILD SUCCESS). The build is green only while failIfNoTests is not set: with -DfailIfNoTests=true the second mvn command ends in BUILD FAILURE, 'No tests were executed!'. The test runs again (Tests run: 1) with Surefire 3.6.1 in pom.xml in place of 3.5.4, or with org.junit.vintage:junit-vintage-engine 6.1.3 added to the POM as a test dependency. Assertions off (-DenableAssertions=false -DargLine=-Dtests.asserts=false) changes neither output. On a build of main 781025d6fe32 set V=12.0.0-SNAPSHOT: the second mvn command gives the same as on the branch. Run on Linux aarch64 only; Maven 4 and other JDKs were not run.
- This finding was re-run on Lucene main b5a4546a5964 on 2026-11-22: still reproduces; in the commands below, V=12.0.0-SNAPSHOT on a build of main b5a4546a5964.
- How to read the outcome: the finding reproduces when the second mvn command prints 'Tests run: 0' and BUILD SUCCESS where the first printed 'Tests run: 1': the test is not executed and nothing fails.

```bash
# needs Maven 3.9.16 (mvn) and JDK 25 on the PATH; run in a copy of this folder
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT   # a Lucene checkout of branch_11x at daa54f2e0cd3
# the 11.0 artifacts, from the checkout: lucene-test-framework, lucene-core and lucene-codecs $V in $LUCENE/build/maven-artifacts
(cd $LUCENE && ./gradlew --no-daemon :lucene:test-framework:publishJarsPublicationToBuildRepository :lucene:core:publishJarsPublicationToBuildRepository :lucene:codecs:publishJarsPublicationToBuildRepository)
echo "<settings><mirrors><mirror><id>own-build</id><mirrorOf>apache.snapshots</mirrorOf><url>file://$LUCENE/build/maven-artifacts</url></mirror></mirrors></settings>" > consumer/settings.xml
(cd consumer && mvn -B -Dlucene.version=10.6.0 clean test)        # expected and observed: Tests run: 1, BUILD SUCCESS
(cd consumer && mvn -B -s settings.xml -Dlucene.version=$V clean test)   # expected: Tests run: 1; observed: Tests run: 0, BUILD SUCCESS
```

## Files shown on the finding page

- [pom.xml](pom.xml)
- [TestConsumer.java](TestConsumer.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
