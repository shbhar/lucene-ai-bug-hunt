# Two class javadocs of the new Albanian analysis package name c as the letter that ç is folded to (AlbanianNormalizationFilter: 'ë/ç are folded to e/c'; AlbanianLightStemmer: input 'normalized by AlbanianNormalizer' with 'ç→c'), but AlbanianNormalizer writes q for the precomposed ç: in the test the filter turns çfarë into 'qfare' (documentation defect on branch_11x and main, both unreleased; the q folding is intended and no code change is claimed here)

Finding LFV-1573 · Area: analysis-common / Albanian (org.apache.lucene.analysis.sq, new on main and branch_11x): the class javadocs of AlbanianNormalizationFilter and AlbanianLightStemmer against AlbanianNormalizer · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1573.html](../../bugs/LFV-1573.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You read the class javadoc of the new AlbanianNormalizationFilter, which says that ë/ç 'are folded to e/c' (the class javadoc of AlbanianLightStemmer says the same of the input it expects: 'ç→c'), and you expect the word çfarë to come out of the filter as 'cfare'. Instead it comes out as 'qfare': AlbanianNormalizer, the class both sentences point to, writes q for ç on purpose, as its own javadoc and the package's tests say (run with the precomposed letter on branch_11x 3221dd4cf3a1 and main 7b8418faa2ff, with and without assertions). The defect is in the two sentences: the q folding is intended, this finding proposes no code change, and no release has the package yet.

## Expected vs actual

- Expected: The term `cfare`, as the class javadoc of `AlbanianNormalizationFilter` says ("ë/ç are folded to e/c").
- Actual: The term `qfare`: `term 0 expected:<[c]fare> but was:<[q]fare>`, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/sq/TestAlbanianFilterJavadocCedilla.java](src/org/apache/lucene/analysis/sq/TestAlbanianFilterJavadocCedilla.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene branch_11x 3221dd4cf3a1 (a commit of 2026-11-20): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there. The test asserts what the filter's class javadoc says ('cfare') to show the mismatch; it is not a regression test: the package's own test TestAlbanianNormalizer expects 'qfare' for the same word, and the q folding is intended.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.sq.TestAlbanianFilterJavadocCedilla
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestAlbanianFilterJavadocCedilla.java](TestAlbanianFilterJavadocCedilla.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
