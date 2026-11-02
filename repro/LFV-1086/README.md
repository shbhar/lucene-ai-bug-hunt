# The 10.6.0 documentation front page says the Migration Guide covers migrating 'from Lucene 0.x' (main builds: 'Lucene 12; ... from Lucene 2.x'), because the build binds majorVersion as a String and the template's ${project.majorVersion-1} then deletes the first '1' character instead of subtracting (documentation build defect)

Finding LFV-1086 · Area: build-tools / documentation build (DocumentationConfigPlugin createDocumentationIndex template binding; lucene/documentation index.template.md) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1086.html](../../bugs/LFV-1086.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open the documentation front page of Lucene 10.6.0 (docs/index.html) and read the Migration Guide entry. You expect 'What changed in Lucene 10; how to migrate code from Lucene 9.x.', as 10.5.2 says, because the template computes the previous major as ${project.majorVersion-1}; instead it says 'how to migrate code from Lucene 0.x.' (a main build says 'Lucene 12; ... from Lucene 2.x.'). Since the build's version globals became Java (#14862 on main, backported to 10.x in #16592), the template gets majorVersion as the String '10', and Groovy's String minus 1 deletes the first '1' character instead of subtracting; binding the value as an int restores 'from Lucene 9.x' (main: '11.x').

## Expected vs actual

- Expected: The sentence names N-1: "Lucene 12; how to migrate code from Lucene 11.x" on main (10.6.0: "Lucene 10; ... from Lucene 9.x").
- Actual: `AssertionError: expected:<11> but was:<2>` on a main build ("from Lucene 2.x"); `expected:<9> but was:<0>` on the 10.6.0 release page ("from Lucene 0.x").

## Files

- [src/org/apache/lucene/TestMigrationGuidePreviousMajor.java](src/org/apache/lucene/TestMigrationGuidePreviousMajor.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- The test reads index.html from the documentation tree named by the system property docs.dir, which the java command below leaves out. Build the page first with ./gradlew :lucene:documentation:createDocumentationIndex and add -Ddocs.dir=$LUCENE/lucene/documentation/build/site to the java command (with gradle: "-Ptests.jvmargs=-Ddocs.dir=$LUCENE/lucene/documentation/build/site"), or point docs.dir at the docs/ folder of an unpacked lucene-10.6.0 binary release. Without docs.dir the test fails with a NullPointerException, which is not the finding. The Lucene jars play no part: the page text decides the result.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.TestMigrationGuidePreviousMajor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMigrationGuidePreviousMajor.java](TestMigrationGuidePreviousMajor.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
