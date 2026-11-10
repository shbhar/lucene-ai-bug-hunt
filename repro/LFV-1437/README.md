# TokenizerFactory, TokenFilterFactory and CharFilterFactory javadocs say forName, lookupClass and the available-names lists look up factories 'from context classpath', but since 7.0 only the class loader that defined the facade is scanned, so a factory registered only through the thread context class loader is reported as missing (javadoc only; the behaviour is intended); since 7.0.0

Finding LFV-1437 · Area: analysis / factory SPI lookup (TokenizerFactory, TokenFilterFactory and CharFilterFactory method javadocs; AnalysisSPILoader scans the defining class loader) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1437.html](../../bugs/LFV-1437.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You put a custom token filter factory with a META-INF/services registration in a child class loader, make that loader the thread's context class loader and call TokenFilterFactory.forName("onlyOnContextLoader", args). You expect it to be found, because the javadoc of forName, lookupClass and availableTokenFilters (and of the TokenizerFactory and CharFilterFactory twins, eight lines in all) says the lookup is 'from context classpath'. Instead forName throws IllegalArgumentException '... does not exist. You need to add the corresponding JAR file supporting this SPI to your classpath' and availableTokenFilters() does not list the factory: since LUCENE-7873 (7.0.0) the lookup deliberately scans only the class loader that defined the facade class, the eight method javadocs were never updated, and the factory is found only after an explicit reloadTokenFilters(contextLoader).

## Expected vs actual

- Expected: `TokenFilterFactory.forName("onlyOnContextLoader", ...)` returns the factory registered on the thread's context class loader, as its javadoc says.
- Actual: `IllegalArgumentException: ... with name 'onlyOnContextLoader' does not exist. You need to add the corresponding JAR file supporting this SPI to your classpath`, with assertions on and off, on main and 10.6.0.

## Files

- [src/org/apache/lucene/analysis/TestFactoryLookupContextClassLoader.java](src/org/apache/lucene/analysis/TestFactoryLookupContextClassLoader.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestFactoryLookupContextClassLoader
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFactoryLookupContextClassLoader.java](TestFactoryLookupContextClassLoader.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
