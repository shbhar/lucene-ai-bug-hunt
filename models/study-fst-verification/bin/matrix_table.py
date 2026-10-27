# Build the README matrix table from logs/matrix.log and the first line of each logs/bx-<tag>.log
import re, sys, os
E = sys.argv[1]
rows = []
tot_f = tot_c = 0
for line in open(os.path.join(E, 'logs/matrix.log')):
    m = re.match(r'(\S+) (\d+)s DONE fsts=(\d+) checks=(\d+) fails=(\d+) tieOrderMismatches=(\d+) nodes=(\d+) bs=(\d+) da=(\d+) cont=(\d+)', line)
    if not m: continue
    tag = m.group(1)
    head = open(os.path.join(E, f'logs/bx-{tag}.log')).readline()
    s = re.search(r'universe=(\d+) strings, sets=(\d+) probes=(\d+) cfg=(\S+) pairMode=(\d) outExhaustMax=(\d+) posDomain=(\d+) thresholdProps\(depth,shallow,deep\)=(\S+)', head)
    thr = s.group(8)
    thr = 'real' if thr.startswith('real') else ('t1' if thr.endswith(',1,1') else 't2')
    pm = {'0': '-', '1': 'all pairs', '2': 'random seq'}[s.group(5)]
    rows.append(f"| {tag} | {thr} | `{s.group(4)}` | {s.group(1)} / {s.group(2)} | {s.group(3)} | {pm} | {int(m.group(3)):,} | {int(m.group(4)):,} | {m.group(8)}/{m.group(9)}/{m.group(10)} of {m.group(7)} | {m.group(5)} | {m.group(2)} s |")
    tot_f += int(m.group(3)); tot_c += int(m.group(4))
print("| run | thresholds | config | universe / key sets | base probes | stateful | FSTs | checks | BS/DA/CONT of nodes | fails | time |")
print("|---|---|---|---|---|---|---|---|---|---|---|")
print("\n".join(rows))
print(f"| **total** | | | | | | **{tot_f:,}** | **{tot_c:,}** | | **0** | |")
