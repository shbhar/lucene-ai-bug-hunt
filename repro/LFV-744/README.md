# IndriDirichletSimilarity(float mu) uses LMSimilarity's default (cf+1)/(|C|+1) collection model instead of IndriCollectionModel (cf/|C|), so new IndriDirichletSimilarity(2000f) scores differently from new IndriDirichletSimilarity although both print the same name

Bug LFV-744 · Area: search / similarities (IndriDirichletSimilarity constructors and the LMSimilarity collection model) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-744.html](../../bugs/LFV-744.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You install new IndriDirichletSimilarity(2000f) and expect the same similarity as new IndriDirichletSimilarity, which is documented as using 'the default mu value of 2000', and Indri's Dirichlet smoothing log((tf + mu*cf/|C|)/(|D| + mu)) for any mu. Instead the mu-only constructor passes no collection model, so its implicit super installs LMSimilarity's DefaultCollectionModel, (cf+1)/(|C|+1), while only the no-argument constructor installs the class's own IndriCollectionModel, cf/|C|. On a 25-token index the term 'president' (cf 3) scores -1.871553 instead of -2.119101 in the same document, although both instances print 'LM IndriDirichlet(2000.000000)'.

## Expected vs actual

- Expected: `IndriDirichletSimilarity(2000f)` scores doc 0 for "president" -2.119101, like `IndriDirichletSimilarity`; `IndriDirichletSimilarity(3f)` scores the "george" document -1.9661129, as Indri's formula says.
- Actual: -1.871553 and -1.8718022: the mu constructor uses (cf + 1) / (|C| + 1) as the collection probability (`explain`: 0.15384616 instead of 0.12).

## Files

- [src/org/apache/lucene/search/similarities/TestIndriDirichletMuConstructor.java](src/org/apache/lucene/search/similarities/TestIndriDirichletMuConstructor.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.similarities.TestIndriDirichletMuConstructor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestIndriDirichletMuConstructor.java](TestIndriDirichletMuConstructor.java)
- [IndriCtorExplainProbe.java](IndriCtorExplainProbe.java)
- [3-TestIndriDirichletMuConstructor.java](3-TestIndriDirichletMuConstructor.java)
- [TestIndriMuCtorRankingV3.java](TestIndriMuCtorRankingV3.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
