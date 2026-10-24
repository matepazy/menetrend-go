"""Bounded emulator capture helper for the layout consistency pass."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:\Users\Mate\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = Path('.impeccable/review/layout')
OUT.mkdir(parents=True, exist_ok=True)
def adb(*args):
    return subprocess.check_output([ADB, '-s', 'emulator-5554', *args], stderr=subprocess.STDOUT)
def nodes():
    result = adb('shell', 'uiautomator', 'dump', '/sdcard/layout-review.xml')
    if b'dumped to' not in result:
        raise RuntimeError(result.decode())
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/layout-review.xml')).iter('node'))
def tap(label):
    match = next((n for n in nodes() if n.get('text') == label or n.get('content-desc') == label), None)
    if match is None:
        raise RuntimeError('Missing control: '+label)
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',match.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(1)
def shot(name):
    tree=nodes()
    (OUT/(name+'.xml')).write_bytes(adb('shell','cat','/sdcard/layout-review.xml'))
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    print(name+': '+ ' | '.join(n.get('text') for n in tree if n.get('text')),flush=True)
def start():
    adb('shell','am','force-stop','hu.matepazy.menetrendgo')
    adb('shell','am','start','-n','hu.matepazy.menetrendgo/.MainActivity')
    time.sleep(2)
