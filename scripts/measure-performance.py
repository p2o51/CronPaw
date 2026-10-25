#!/usr/bin/env python3
"""Measure a configured 1600x2560 portrait, one-day CronPaw view without changing data.

Uses force-stop/start and swipe gestures. LaunchState is retained: a widget may
restart the process before the activity launches, so these are not all cold starts.
Raw dumps/screenshots are local artifacts and may contain calendar data.
"""
import argparse
import subprocess, time, pathlib, re, json
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default='adb')
parser.add_argument('--port', type=int, default=5037)
parser.add_argument('--serial', required=True)
parser.add_argument('--out', type=pathlib.Path, required=True)
args = parser.parse_args()
adb = [args.adb, '-P', str(args.port), '-s', args.serial]
out = args.out
out.mkdir(parents=True, exist_ok=False)
def shell(*args): return subprocess.check_output(adb+['shell',*args],text=True)
def save(name,text): (out/name).write_text(text)
def metrics(name):
    t=shell('dumpsys','gfxinfo','com.gpo.cronpaw','framestats'); save(name+'.txt',t)
    print(name, '\n'.join(dict.fromkeys(l for l in t.splitlines() if any(k in l for k in ['Total frames','Janky frames:','percentile:','Slow UI thread','deadline missed:']))), flush=True)
size = shell('wm', 'size')
if size.strip() != 'Physical size: 1600x2560':
    raise SystemExit('This recorded gesture sequence requires a 1600x2560 display without a size override.')
launches=[]
launch_states=[]
for i in range(5):
    shell('am','force-stop','com.gpo.cronpaw')
    time.sleep(.5)
    t=shell('am','start','-W','-n','com.gpo.cronpaw/.MainActivity')
    save('launch-'+str(i)+'.txt',t)
    launches.append(int(re.search(r'TotalTime: (\d+)',t)[1]))
    launch_states.append(re.search(r'LaunchState: (.+)',t)[1])
    time.sleep(3)
print('restart_launch_ms',launches, 'LaunchState',launch_states,flush=True)
save('launches.json',json.dumps({'milliseconds':launches,'launch_states':launch_states}))
save('memory-before.txt',shell('dumpsys','meminfo','com.gpo.cronpaw'))
save('thermal.txt',shell('dumpsys','thermalservice'))
for name in ['vertical','horizontal']:
    shell('input','tap','1560','135'); time.sleep(1)
    shell('dumpsys','gfxinfo','com.gpo.cronpaw','reset')
    for i in range(8):
        if name=='vertical':
            coords = ['850','1800','850','750'] if i%2==0 else ['850','750','850','1800']
        else:
            coords = ['1300','1000','400','1000'] if i%2==0 else ['400','1000','1300','1000']
        shell('input','swipe',*coords,'350'); time.sleep(.45)
    time.sleep(1); metrics(name)
shell('input','tap','1560','135'); time.sleep(1)
shell('dumpsys','gfxinfo','com.gpo.cronpaw','reset'); time.sleep(5); metrics('idle')
save('memory-after.txt',shell('dumpsys','meminfo','com.gpo.cronpaw'))
(out/'final.png').write_bytes(subprocess.check_output(adb+['exec-out','screencap','-p']))
