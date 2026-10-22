"""Small ADB helper used for reproducible verification of this app only."""
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

SERIAL = 'emulator-5556'
ROOT = pathlib.Path('.impeccable/review')
ROOT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(['adb', '-s', SERIAL, *args],stderr=subprocess.STDOUT)


def nodes():
    for attempt in range(4):
        result=adb('shell', 'uiautomator', 'dump', '/sdcard/menetrendgo-qa.xml')
        if b'dumped to' in result:
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/menetrendgo-qa.xml')).iter('node'))
        time.sleep(1)
    raise RuntimeError('Android accessibility tree did not settle: '+result.decode(errors='replace'))


def tap(label, desc=False):
    key = 'content-desc' if desc else 'text'
    matches = [n for n in nodes() if n.get(key) == label]
    if not matches:
        raise AssertionError(f'Control not found: {label}')
    b = list(map(int, re.findall(r'\d+', matches[0].get('bounds'))))
    adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
    time.sleep(.5)


def shot(name, expected):
    text = ' '.join(n.get('text', '') for n in nodes())
    assert expected in text, (name, expected, text)
    path = ROOT / f'{name}.png'
    path.write_bytes(adb('exec-out', 'screencap', '-p'))
    print(f'Captured {path}: {expected}')


def dump():
    print('\n'.join(str((n.get('text'), n.get('content-desc'), n.get('bounds')))
                    for n in nodes() if n.get('text') or n.get('content-desc')))


if __name__ == '__main__':
    dump()
