# Serializability check: is every concurrent final graph equal to the final graph of SOME
# sequential insertion order?  Stage 1 enumerates all insertion orders with W=1 (TLC, PRINT_FINAL),
# stage 2 model-checks W workers against the collected set (invariant SerializableView).
# usage: python3 -I scripts/serial.py NAME N W "<sim expr>" "<levels expr>" [FIX_SCORER]
import itertools, os, re, subprocess, sys
D = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
name, n, w, simx, lvlx = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4], sys.argv[5]
fix = sys.argv[6] if len(sys.argv) > 6 else 'TRUE'
perms = list(itertools.permutations(range(n)))
mod = [f'---- MODULE MC_{name} ----', 'EXTENDS MC',
       f'S_Sim == {{ {simx} }}', f'S_Lvl == {{ {lvlx} }}', 'S_Init == { [n \\in Nodes |-> << >>] }',
       'S_Order == << ' + ', '.join(map(str, range(n))) + ' >>']
for i, p in enumerate(perms):
    mod.append(f'P{i} == << ' + ', '.join(map(str, p)) + ' >>')
def write(extra):
    open(f'{D}/model/MC_{name}.tla', 'w').write('\n'.join(mod + extra + ['====']) + '\n')
def cfg(cname, **kv):
    args = ['python3', '-I', f'{D}/scripts/mkcfg.py', cname, f'N={n}', 'SimChoices=S_Sim', 'LevelChoices=S_Lvl',
            'InitChoices=S_Init'] + [f'{k}={v}' for k, v in kv.items()]
    subprocess.run(args, check=True, cwd=D)
def tlc(cname):
    env = dict(os.environ, SPEC=f'MC_{name}', WORKERS='4')
    subprocess.run([f'{D}/scripts/tlc.sh', cname], env=env, cwd=D)
    return open(f'{D}/logs/{cname}.log').read()
write([])
finals = set()
for i, p in enumerate(perms):
    cn = f'{name}_seq{i}'
    cfg(cn, W='1', Order=f'P{i}', PRINT_FINAL='TRUE', FIX_SCORER=fix, INV='NoCodeError')
    log = tlc(cn)
    for m in re.finditer(r'<< "FINAL",(.*?)>>\s*\n(?=\S)', log, re.S):
        finals.add(' '.join(m.group(1).split()))
    os.remove(f'{D}/model/{cn}.cfg')
print(f'{name}: {len(perms)} orders -> {len(finals)} distinct sequential final graphs')
write(['SeqSet == { ' + ', '.join(sorted(finals)) + ' }'])
cn = f'{name}_conc'
cfg(cn, W=str(w), Order='S_Order', FIX_SCORER=fix, SEQ='SeqSet', INV='NoCodeError,SerializableView')
log = tlc(cn)
res = 'VIOLATED' if 'SerializableView is violated' in log else ('HOLDS' if 'No error has been found' in log else 'ERROR')
st = re.findall(r'([0-9,]+) distinct states found', log)
print(f'{name}: concurrent W={w}: SerializableView {res}; distinct states {st[-1] if st else "?"}')
