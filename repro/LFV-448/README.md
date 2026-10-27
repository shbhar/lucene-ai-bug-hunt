# OpenNLP analysis factories cache models and lemma dictionaries JVM-wide by bare file name, so an analyzer built from a second configuration directory silently uses another directory's same-named model (and builds even when its own directory lacks the file); the name namespace and its scope are undocumented (documentation defect of a deliberate design)

Bug LFV-448 · Area: analysis-opennlp / model cache (OpenNLPOpsFactory and the OpenNLP factories' inform/create) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build two analyzers with CustomAnalyzer.builder(configDir) from two configuration directories, each with its own OpenNLP model under the same file name (for example sent.bin and tok.bin, or a lemma dictionary), and expect each analyzer to use its own directory's files, as builder(Path) says it 'loads all resources from the given file system base directory'. Instead the OpenNLP factories keep one static, JVM-wide cache per model type keyed only by the file name, so the second analyzer silently gets the model the first directory loaded: with two valid, different tokenizer models, configB's analyzer produces configA's tokens [Hello, world,., This, is, a, test,.] instead of its own [Hello, world., This, is, a, test.], and a directory with no such file at all still builds. The name-keyed cache is intended (LUCENE-2899), but nothing documents that file names are shared by every configuration in the JVM.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The OpenNLP factories keep one static, JVM-wide cache per model type keyed only by the file name, so the second analyzer silently gets the model the first directory loaded: with two valid, different tokenizer models, configB's analyzer produces configA's tokens [Hello, world,., This, is, a, test,.] instead of its own [Hello, world., This, is, a, test.], and a directory with no such file at all still builds.

## Files

- [src/org/apache/lucene/analysis/opennlp/TestOpenNLPModelCacheIgnoresResourceLoader.java](src/org/apache/lucene/analysis/opennlp/TestOpenNLPModelCacheIgnoresResourceLoader.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/analysis/opennlp/build/libs/lucene-analysis-opennlp-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.opennlp.TestOpenNLPModelCacheIgnoresResourceLoader
```

The module(s) analysis-opennlp also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
