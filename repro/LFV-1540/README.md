# BlendedInfixSuggester.lookup with contexts that have a positive clause scores a fetched suggestion that has no term matching the key and does not start with the raw key as if the match were at position Integer.MAX_VALUE: for a non-empty key that the query analyzer turns into no token (run: the stop word 'the' under EnglishAnalyzer; a space, '?' or '-' under StandardAnalyzer without stop words) and allTermsRequired = true (the default), the default blender returns the lowest of the fetched weights first with large negative values (run with the weights 30, 20, 10), and the lookup throws NullPointerException when such a suggestion's own text has no token (the wrong order in 4.10.4 and 5.0.0 to 10.6.0, the exception from 4.8.0, branch_11x and main)

Finding LFV-1540 · Area: suggest / analyzing: BlendedInfixSuggester blended score (createCoefficient, calculateCoefficient) under AnalyzingInfixSuggester's lookup with contexts · Priority: low · Impact: minor · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1540.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1540.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a BlendedInfixSuggester with its two-argument constructor over the stock EnglishAnalyzer, add three suggestions to one context (dune 30, emma 20, ulysses 10) and call lookup("the", contexts, false, 1). 'the' is a stop word, so the key has no token and the lookup matches every suggestion of the context; AnalyzingInfixSuggester returns dune, the highest weight, for the same call, and BlendedInfixSuggester returns dune for the empty key, so you expect dune. Instead you get ulysses, the lowest weight, with the value -2147483637 (with num 3: ulysses, emma, dune with -2147483637, -4294967274, -6442450911), and no error: createCoefficient starts its search for the closest matching term with position = Integer.MAX_VALUE, no term of a hit matches a key without tokens, and the start value goes into the linear formula 1 - 0.10 * position. If a suggestion of the context is among the fetched hits and its text has no token and does not start with the raw key ('To Be or Not to Be' under EnglishAnalyzer), the same lookup throws NullPointerException, because the method reads the hit's term vector without a null check.

## Expected vs actual

- Expected: `lookup("the", books, false, 1)` returns `dune/30`, the highest weight of the context (by comparison with the parent class), and `to be/10` in the second method.
- Actual: It returns `ulysses/-2147483637` (`ComparisonFailure: expected:<[dune/30]> but was:<[ulysses/-2147483637]>`); the second method throws `NullPointerException` (`"tv" is null`) in `createCoefficient`.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestBlendedInfixSuggesterNoTokenKey.java](src/org/apache/lucene/search/suggest/analyzing/TestBlendedInfixSuggesterNoTokenKey.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene branch_11x 89e68e0f3a50 (a commit of 2026-11-17): reproduced.
- A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there. With the jars of a release the test as shipped also fails 2 of 2 on 9.1.0, 9.12.3, 10.5.2 and 10.6.0. For a release from 5.0.0 to 9.0.0 change one line first: the import of LuceneTestCase becomes 'import org.apache.lucene.util.LuceneTestCase;' (with that change it fails 2 of 2 on 5.0.0, 5.5.5, 6.6.6, 7.7.3, 8.11.4 and 9.0.0, run with JDK 25 and the test dependencies of each release; with a JDK before 9 the two Set.of calls have to be replaced as well, javac --release 8 rejects them). A 4.x release needs more changes, and the shipped file was not run there.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestBlendedInfixSuggesterNoTokenKey
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestBlendedInfixSuggesterNoTokenKey.java](TestBlendedInfixSuggesterNoTokenKey.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
