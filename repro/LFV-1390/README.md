# galician.rslp repeats the Noun rule {"ido",3} where the rules page GalicianStemmer cites has "ído", so the first 'ido' rule's exception list (cándido, cândido, consolido, decidido, duvido, marido, rápido) never applies and GalicianAnalyzer stems 'marido' (husband) to 'mar', the same term as 'mar' (sea)

Finding LFV-1390 · Area: analysis-common / Galician stemmer rule file (galician.rslp Noun step, run by RSLPStemmerBase; GalicianStemFilter, GalicianAnalyzer and the 'galicianStem' factory) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1390.html](../../bugs/LFV-1390.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Galician text with the stock GalicianAnalyzer; one document says 'marido' (husband) and a query says 'mar' (sea). You expect no match: galician.rslp's Noun step lists 'marido' (with cándido, cândido, consolido, decidido, duvido and rápido) as an exception of its 'ido' rule at line 219, so, as in the rules page that GalicianStemmer says it implements, the word keeps 'ido' and only the final vowel is removed ('marid'). Instead line 224, under the comment '# mantído -&gt; mant', is an exact copy of that rule without the exception list where the cited page has 'ído', and because the first matching rule wins it removes 'ido' anyway: 'marido' is indexed as 'mar' and the query for 'mar' finds the document, with no error.

## Expected vs actual

- Expected: "marido" (husband) is analyzed to the single term `marid`, and the test passes.
- Actual: The term is `mar`, the term of "mar" (sea): `term 0 expected:<mar[id]> but was:<mar[]>`.

## Files

- [src/org/apache/lucene/analysis/gl/TestGalicianNounIdoExceptions.java](src/org/apache/lucene/analysis/gl/TestGalicianNounIdoExceptions.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.gl.TestGalicianNounIdoExceptions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestGalicianNounIdoExceptions.java](TestGalicianNounIdoExceptions.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
