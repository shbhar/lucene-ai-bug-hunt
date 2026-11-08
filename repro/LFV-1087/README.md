# The documentation front page's 'API Javadocs' list links benchmark-jmh/index.html, a page no build renders: the Java port of the documentation build (#15350, backported by #16592) lists every module that has a renderSiteJavadoc task, while the Groovy build it replaced listed only modules whose task is enabled (dead link in the released 10.6.0 docs and in documentation builds of main, branch_11x and branch_10_6; documentation defect)

Finding LFV-1087 · Area: build-tools / documentation build (DocumentationConfigPlugin.MarkdownTemplateTask.withProjectList: the 'API Javadocs' list of the documentation front page docs/index.html) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1087.html](../../bugs/LFV-1087.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open the front page of the Lucene 10.6.0 documentation (docs/index.html in the binary release, or a documentation build of main) and click benchmark-jmh in its 'API Javadocs' list. You expect that module's javadocs, as for the other 31 entries; instead the page does not exist, because benchmark-jmh's build disables its site javadocs ('No special javadocs for JMH benchmarks') and the Java port of the documentation build lists every module that has a renderSiteJavadoc task without checking that the task is enabled, which the Groovy build up to 10.5.2 did. Adding '&amp;&amp; t.getEnabled()' to that filter removes exactly this entry and nothing else.

## Expected vs actual

- Expected: The test passes: every "API Javadocs" link resolves to a page in the documentation tree.
- Actual: `AssertionError: expected:<[]> but was:<[benchmark-jmh/index.html]>`: the page links a folder the build never writes.

## Files

- [src/org/apache/lucene/TestFrontPageJavadocLinks.java](src/org/apache/lucene/TestFrontPageJavadocLinks.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- The test reads index.html and the module javadoc folders of the documentation tree named by the system property docs.dir, which the java command below leaves out. Build the full documentation first with ./gradlew :lucene:documentation:documentation and add -Ddocs.dir=$LUCENE/lucene/documentation/build/site to the java command (with gradle: "-Ptests.jvmargs=-Ddocs.dir=$LUCENE/lucene/documentation/build/site"), or point docs.dir at the docs/ folder of an unpacked lucene-10.6.0 binary release. Without docs.dir the test fails with a NullPointerException, which is not the finding. The Lucene jars play no part: the documentation tree decides the result.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.
- Not runnable from this site as is: this repro also needs extra JVM setup (a system property or an instrumentation hook, for example) that is not exported here; see the comments at the top of the source.

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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.TestFrontPageJavadocLinks
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFrontPageJavadocLinks.java](TestFrontPageJavadocLinks.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
