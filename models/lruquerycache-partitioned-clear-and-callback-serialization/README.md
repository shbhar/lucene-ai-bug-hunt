# Formal models and scripts: lruquerycache-partitioned-clear-and-callback-serialization

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- On main, LRUQueryCache.clear empties its partitions one at a time and then calls onClear with no lock held, so an entry cached during clear survives while a subclass that resets its statistics in onClear drops it (it reports 0 entries for a cache holding 1, and -1 once that entry is evicted); regression from #15558, main only (bug LFV-991; [repro](../../repro/LFV-991/))
- On main, LRUQueryCache.cleanUp is synchronized on the cache object and takes partition locks while it holds that monitor, so a subclass whose statistics callbacks synchronize on the cache (synchronized methods or synchronized (this)) deadlocks the cleanup thread against a search, and every later search whose callback needs the monitor hangs too; regression from #15558, main only (bug LFV-1001; [repro](../../repro/LFV-1001/))
- On main, LRUQueryCache's class javadoc still says its statistics callbacks are 'called synchronously and under a lock', but since #15558 the callbacks of different partitions run at the same time and onClear runs under no lock, and neither the javadoc nor MIGRATE.md tells subclasses that their statistics must now be thread-safe (documentation defect, main only) (bug LFV-1000; [repro](../../repro/LFV-1000/))

## Files

- [lruquerycache-callbacks-run-concurrently-across-partitions/model/B_main_sync_noclear_serial.cfg](lruquerycache-callbacks-run-concurrently-across-partitions/model/B_main_sync_noclear_serial.cfg) (used for LFV-1000)
- [lruquerycache-callbacks-run-concurrently-across-partitions/model/C_main_plain_noclear.cfg](lruquerycache-callbacks-run-concurrently-across-partitions/model/C_main_plain_noclear.cfg) (used for LFV-1000)
- [lruquerycache-callbacks-run-concurrently-across-partitions/model/D_rc_plain_all_serial.cfg](lruquerycache-callbacks-run-concurrently-across-partitions/model/D_rc_plain_all_serial.cfg) (used for LFV-1000)
- [lruquerycache-callbacks-run-concurrently-across-partitions/model/D_rc_plain_all_stats.cfg](lruquerycache-callbacks-run-concurrently-across-partitions/model/D_rc_plain_all_stats.cfg) (used for LFV-1000)
- [lruquerycache-callbacks-run-concurrently-across-partitions/model/E_fix_sync_all_serial.cfg](lruquerycache-callbacks-run-concurrently-across-partitions/model/E_fix_sync_all_serial.cfg) (used for LFV-1000)
- [lruquerycache-callbacks-run-concurrently-across-partitions/model/F_fix_plain_noclear.cfg](lruquerycache-callbacks-run-concurrently-across-partitions/model/F_fix_plain_noclear.cfg) (used for LFV-1000)
- [lruquerycache-callbacks-run-concurrently-across-partitions/model/LruPartClear.tla](lruquerycache-callbacks-run-concurrently-across-partitions/model/LruPartClear.tla) (used for LFV-1000)
- [lruquerycache-callbacks-run-concurrently-across-partitions/model/MC_LruPartClear.tla](lruquerycache-callbacks-run-concurrently-across-partitions/model/MC_LruPartClear.tla) (used for LFV-1000)
- [lruquerycache-cleanup-holds-instance-monitor-deadlock/model/LruPartClear.tla](lruquerycache-cleanup-holds-instance-monitor-deadlock/model/LruPartClear.tla) (used for LFV-1001)
- [lruquerycache-cleanup-holds-instance-monitor-deadlock/model/LruPartClearMon.tla](lruquerycache-cleanup-holds-instance-monitor-deadlock/model/LruPartClearMon.tla) (used for LFV-1001)
- [lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M1_main_mon_cleanupsync.cfg](lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M1_main_mon_cleanupsync.cfg) (used for LFV-1001)
- [lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M2_main_mon_cleanupprivate.cfg](lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M2_main_mon_cleanupprivate.cfg) (used for LFV-1001)
- [lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M3_fix_mon_clear_cleanupprivate.cfg](lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M3_fix_mon_clear_cleanupprivate.cfg) (used for LFV-1001)
- [lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M3v_fix_mon_clear_cleanupprivate_vacuity.cfg](lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M3v_fix_mon_clear_cleanupprivate_vacuity.cfg) (used for LFV-1001)
- [lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M4_fix_mon_clear_cleanupsync.cfg](lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M4_fix_mon_clear_cleanupsync.cfg) (used for LFV-1001)
- [lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M5_main_nomon_cleanupsync.cfg](lruquerycache-cleanup-holds-instance-monitor-deadlock/model/M5_main_nomon_cleanupsync.cfg) (used for LFV-1001)
- [lruquerycache-cleanup-holds-instance-monitor-deadlock/model/MC_LruPartClearMon.tla](lruquerycache-cleanup-holds-instance-monitor-deadlock/model/MC_LruPartClearMon.tla) (used for LFV-1001)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/A3_main_sync_clear_cleanup.cfg](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/A3_main_sync_clear_cleanup.cfg) (used for LFV-991)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/A_main_sync_clear.cfg](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/A_main_sync_clear.cfg) (used for LFV-991)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/B_main_sync_noclear_stats.cfg](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/B_main_sync_noclear_stats.cfg) (used for LFV-991)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/D_rc_plain_all_stats.cfg](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/D_rc_plain_all_stats.cfg) (used for LFV-991)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/D_rc_plain_all_vacuity.cfg](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/D_rc_plain_all_vacuity.cfg) (used for LFV-991)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/E_fix_sync_all_stats.cfg](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/E_fix_sync_all_stats.cfg) (used for LFV-991)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/E_fix_sync_all_vacuity.cfg](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/E_fix_sync_all_vacuity.cfg) (used for LFV-991)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/G_main_sync_clear_onepart.cfg](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/G_main_sync_clear_onepart.cfg) (used for LFV-991)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/LruPartClear.tla](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/LruPartClear.tla) (used for LFV-991)
- [lruquerycache-clear-not-atomic-with-concurrent-inserts/model/MC_LruPartClear.tla](lruquerycache-clear-not-atomic-with-concurrent-inserts/model/MC_LruPartClear.tla) (used for LFV-991)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
