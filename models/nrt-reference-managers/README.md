# Formal models and scripts: nrt-reference-managers

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- SearcherTaxonomyManager.acquire() spins forever at 100% CPU, instead of throwing the LUCENE-5436 IllegalStateException, when the current pair's taxonomy reader was closed outside the manager (finding LFV-250; [repro](../../repro/LFV-250/))
- ControlledRealTimeReopenThread.close() never unregisters its refresh listener, so one later refresh of the still-open ReferenceManager overwrites the Long.MAX_VALUE 'closed' generation and an untimed waitForGeneration on the closed instance can block forever (finding LFV-252; [repro](../../repro/LFV-252/), [finding page](../../bugs/LFV-252.html))
- A ReferenceManager refresh that throws is reported to its refresh listeners as afterRefresh(false), the same signal as 'nothing changed', so ControlledRealTimeReopenThread.waitForGeneration returns true for a change the current searcher does not contain and LiveFieldValues drops the values it buffered (finding LFV-312; [repro](../../repro/LFV-312/))
- SearcherTaxonomyManager refresh calls t2.addSuppressed(t2), so when the taxonomy reopen fails and closing the just-reopened index reader also fails, maybeRefresh throws IllegalArgumentException ('Self-suppression not permitted') and the taxonomy failure is lost (finding LFV-264; [repro](../../repro/LFV-264/), [finding page](../../bugs/LFV-264.html))
- SearcherTaxonomyManager leaks a taxonomy reader (an extra reference on the current one, or a whole newly opened reader with its open files) when the SearcherFactory throws during a refresh, and the NRT and Directory constructors leak theirs when it throws on the first searcher (finding LFV-309; [repro](../../repro/LFV-309/))

## Files

- [crt-close-sentinel-overwritten-by-later-refresh/model/CRT.tla](crt-close-sentinel-overwritten-by-later-refresh/model/CRT.tla) (used for LFV-252)
- [crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_app.cfg](crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_app.cfg) (used for LFV-252)
- [crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_appafterclose.cfg](crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_appafterclose.cfg) (used for LFV-252)
- [crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_appafterclose_fix.cfg](crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_appafterclose_fix.cfg) (used for LFV-252)
- [crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_apprace.cfg](crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_apprace.cfg) (used for LFV-252)
- [crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_base.cfg](crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_base.cfg) (used for LFV-252)
- [crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_reblock.cfg](crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_reblock.cfg) (used for LFV-252)
- [crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_reblock_fix.cfg](crt-close-sentinel-overwritten-by-later-refresh/model/MC_CRT_reblock_fix.cfg) (used for LFV-252)
- [refresh-failure-reported-as-no-change/model/CRT.tla](refresh-failure-reported-as-no-change/model/CRT.tla) (used for LFV-312)
- [refresh-failure-reported-as-no-change/model/MC_CRT_refreshfail.cfg](refresh-failure-reported-as-no-change/model/MC_CRT_refreshfail.cfg) (used for LFV-312)
- [refresh-failure-reported-as-no-change/model/MC_CRT_refreshfail_fix.cfg](refresh-failure-reported-as-no-change/model/MC_CRT_refreshfail_fix.cfg) (used for LFV-312)
- [refresh-failure-reported-as-no-change/model/MC_RefMgr_base.cfg](refresh-failure-reported-as-no-change/model/MC_RefMgr_base.cfg) (used for LFV-312)
- [refresh-failure-reported-as-no-change/model/MC_RefMgr_refreshfail.cfg](refresh-failure-reported-as-no-change/model/MC_RefMgr_refreshfail.cfg) (used for LFV-312)
- [refresh-failure-reported-as-no-change/model/MC_RefMgr_refreshfail_fix.cfg](refresh-failure-reported-as-no-change/model/MC_RefMgr_refreshfail_fix.cfg) (used for LFV-312)
- [refresh-failure-reported-as-no-change/model/RefMgr.tla](refresh-failure-reported-as-no-change/model/RefMgr.tla) (used for LFV-312)
- [searchertaxonomymanager-acquire-spins-when-taxonomy-reader-closed/model/RefMgr.tla](searchertaxonomymanager-acquire-spins-when-taxonomy-reader-closed/model/RefMgr.tla) (used for LFV-250)
- [searchertaxonomymanager-leaks-taxonomy-reader-on-searcherfactory-failure/model/RefMgr.tla](searchertaxonomymanager-leaks-taxonomy-reader-on-searcherfactory-failure/model/RefMgr.tla) (used for LFV-309)
- [searchertaxonomymanager-self-suppression-masks-exception/model/RefMgr.tla](searchertaxonomymanager-self-suppression-masks-exception/model/RefMgr.tla) (used for LFV-264)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).
