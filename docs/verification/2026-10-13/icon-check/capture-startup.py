import subprocess, time, threading
from pathlib import Path
adb = str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe')
out = Path('docs/verification/2026-10-13/icon-check')
subprocess.run([adb, 'shell', 'am', 'force-stop', 'com.veritas.reader.iconcheck'], check=True)
def capture(index, delay):
    time.sleep(delay)
    remote = f'/sdcard/vern-icon-check-splash-{index}.png'
    subprocess.run([adb, 'shell', 'screencap', '-p', remote], check=True, stdout=subprocess.DEVNULL)
    subprocess.run([adb, 'pull', remote, str(out / f'splash-{index}.png')], check=True, stdout=subprocess.DEVNULL)
threads = [threading.Thread(target=capture, args=(i, delay)) for i, delay in enumerate([.1, .3, .55, .85, 1.3])]
launch = subprocess.Popen([adb, 'shell', 'am', 'start', '-W', '-n', 'com.veritas.reader.iconcheck/com.veritas.reader.MainActivity'], stdout=subprocess.PIPE, text=True)
for thread in threads: thread.start()
for thread in threads: thread.join()
print(launch.communicate()[0])
