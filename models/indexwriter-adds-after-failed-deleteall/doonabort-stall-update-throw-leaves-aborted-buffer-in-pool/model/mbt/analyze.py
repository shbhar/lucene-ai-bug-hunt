#!/usr/bin/env python3
"""Unit indexwriter-adds-after-failed-deleteall: summarise a verbose run log of MbtAfterFailedDeleteAll by the shape of the operation sequence.
Usage: analyze.py RUN_LOG  (prints a table; no file is written)"""
import re, sys, collections
rows = []
for line in open(sys.argv[1], errors='replace'):
    if not (line.startswith('ok ') or line.startswith('VIOLATION ')):
        continue
    m = re.match(r'(ok|VIOLATION) prefix=(\S+) maxBufferedDocs=(\S+) ops=(\S+) end=(\S+) :: (.*?) :: \[(.*?)\](?: :: \[(.*)\])? :: acked=(-?\d+) lostAtClose=(-?\d+)', line)
    if not m:
        m2 = re.match(r'(ok) prefix=(\S+) maxBufferedDocs=(\S+) ops=(\S+) end=(\S+) :: (.*?) :: \[(.*?)\] :: acked=(-?\d+) lostAtClose=(-?\d+)', line)
        if not m2:
            print('UNPARSED', line[:200]); continue
        g = m2.groups(); rows.append(dict(st=g[0], prefix=g[1], mbd=g[2], ops='' if g[3] == '-' else g[3], end=g[4], trace=g[5], viol='', notes=g[6], acked=int(g[7]), lost=int(g[8])))
        continue
    g = m.groups()
    if g[0] == 'ok':
        rows.append(dict(st='ok', prefix=g[1], mbd=g[2], ops='' if g[3] == '-' else g[3], end=g[4], trace=g[5], viol='', notes=g[6], acked=int(g[8]), lost=int(g[9])))
    else:
        rows.append(dict(st='VIOLATION', prefix=g[1], mbd=g[2], ops='' if g[3] == '-' else g[3], end=g[4], trace=g[5], viol=g[6], notes=g[7] or '', acked=int(g[8]), lost=int(g[9])))
print('sequences parsed:', len(rows))

def shape(ops):
    """class of a sequence by what comes between the failed deleteAll() and the first add or update"""
    i = next((k for k, c in enumerate(ops) if c in 'AU'), None)
    if i is None:
        return 'no add or update at all'
    before = ops[:i]
    if before == '':
        return 'add/update first'
    if 'X' in before:
        # a second deleteAll() before the first add; what precedes that deleteAll matters
        j = before.index('X')
        return 'second deleteAll() first, then add/update' if before[:j] == '' else 'commit/reader/flush, then second deleteAll(), then add/update'
    return 'commit/reader/flush first, then add/update'

for end in ('close', 'rollback'):
    print('\n== sequences ending in', end + '()')
    agg = collections.OrderedDict()
    for r in rows:
        if r['end'] != end:
            continue
        k = (shape(r['ops']), r['mbd'])
        a = agg.setdefault(k, dict(n=0, viol=0, lost_acked=0, del_na=0, closed_acked=0, some=0, all=0, tragic=0))
        a['n'] += 1
        if r['st'] == 'VIOLATION':
            a['viol'] += 1
            if 'LOST-ACKED' in r['viol']: a['lost_acked'] += 1
            if 'DELETE-NOT-APPLIED' in r['viol']: a['del_na'] += 1
        if '(closed:' in r['trace']: a['tragic'] += 1
        if r['acked'] > 0:
            a['closed_acked'] += 1
            if r['lost'] > 0: a['some'] += 1
            if r['lost'] == r['acked']: a['all'] += 1
    print('%-62s %-8s %6s %6s %10s %8s %11s %9s %8s %8s' % ('shape', 'maxBuf', 'seqs', 'viol', 'lost-acked', 'del-n/a', 'acked@close', 'some-lost', 'all-lost', 'selfclose'))
    for (sh, mbd), a in sorted(agg.items()):
        print('%-62s %-8s %6d %6d %10d %8d %11d %9d %8d %8d' % (sh, mbd, a['n'], a['viol'], a['lost_acked'], a['del_na'], a['closed_acked'], a['some'], a['all'], a['tragic']))

print('\n== the shortest sequences, prefix=add, maxBufferedDocs=default, end=close (length 0..2)')
for r in rows:
    if r['prefix'] == 'add' and r['mbd'] == 'default' and r['end'] == 'close' and len(r['ops']) <= 2:
        t = re.sub(r'deleteAll!\w+@\[[^\]]*\]', 'deleteAll!', r['trace'])
        print('  %-3s %-9s acked=%d lost=%d  %s  %s' % (r['ops'] or '-', r['st'], r['acked'], r['lost'], t, r['viol'][:150]))
