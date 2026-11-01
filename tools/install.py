#!/usr/bin/env python3
"""Build/install on one explicitly selected Android TV; never disables packages."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = 'com.gathin.tvhome'
HOME = APP + '/.MainActivity'
ASSIST = APP + '/' + APP + '.AutoSearchService'
LISTENER = APP + '/' + APP + '.PlaybackMonitorService'
PACKAGE = re.compile(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+')
COMPONENT = re.compile(r'[A-Za-z][A-Za-z0-9_.]*/\.?[A-Za-z][A-Za-z0-9_.$]*')
SECURE_KEYS = ('enabled_accessibility_services', 'accessibility_enabled')


def validate_config(value):
    if not isinstance(value, dict) or set(value) - {'liveTvPackage', 'weather', 'dockApps', 'hiddenApps'}:
        raise ValueError('Unknown device configuration field')
    live = value.get('liveTvPackage', '')
    if not isinstance(live, str) or live and not PACKAGE.fullmatch(live):
        raise ValueError('Invalid liveTvPackage')
    for field in ('dockApps', 'hiddenApps'):
        apps = value.get(field, [])
        if not isinstance(apps, list) or len(apps) > 50 or any(not isinstance(p, str) or not PACKAGE.fullmatch(p) for p in apps):
            raise ValueError('Invalid ' + field)
    if 'weather' in value:
        weather = value['weather']
        if not isinstance(weather, dict) or set(weather) != {'latitude', 'longitude'}:
            raise ValueError('Weather requires latitude and longitude')
        for key, limit in (('latitude', 90), ('longitude', 180)):
            number = weather[key]
            if isinstance(number, bool) or not isinstance(number, (float, int)) or not -limit <= number <= limit:
                raise ValueError('Invalid weather ' + key)
    return value


def merged_services(old, component):
    services = [] if old in ('null', '') else old.split(':')
    if component not in services:
        services.append(component)
    return ':'.join(services)


def sdk_path(explicit=None):
    choices = [explicit, os.getenv('ANDROID_HOME'), os.getenv('ANDROID_SDK_ROOT'),
               str(Path.home() / 'AppData/Local/Android/Sdk'), str(Path.home() / 'Library/Android/sdk'),
               str(Path.home() / 'Android/Sdk')]
    return next((Path(p).expanduser().resolve() for p in choices if p and Path(p).expanduser().is_dir()), None)


def run(command, *, env=None, timeout=120, check=True, data=None):
    result = subprocess.run([str(x) for x in command], input=data, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, env=env, timeout=timeout)
    output = result.stdout.decode('utf-8', errors='replace').strip()
    if check and result.returncode:
        raise RuntimeError(output + '\n' + result.stderr.decode('utf-8', errors='replace').strip())
    return output


class Tv:
    def __init__(self, adb, serial):
        if not serial or not re.fullmatch(r'[A-Za-z0-9_.:\[\]-]{1,128}', serial):
            raise ValueError('Select exactly one device with --device SERIAL_OR_HOST:PORT')
        self.adb, self.serial, self.user = str(adb), serial, "0"

    def command(self, *args, **kwargs):
        args = list(args)
        if args and args[0] == 'install' and '--user' not in args:
            args[1:1] = ['--user', self.user]
        if 'run-as' in args:
            at = args.index('run-as') + 2
            args[at:at] = ['--user', self.user]
        return run([self.adb, '-s', self.serial, *args], **kwargs)

    def shell(self, *args, **kwargs):
        args = list(args)
        if args and args[0] == 'settings' and self.user != '0':
            args[1:1] = ['--user', self.user]
        if args and args[0] == 'run-as':
            args[2:2] = ['--user', self.user]
        return self.command('shell', shlex.join(args), **kwargs)

    def connect(self):
        if ':' in self.serial or '.' in self.serial:
            run([self.adb, 'connect', self.serial], check=False)
        if self.command('get-state') != 'device':
            raise RuntimeError('Authorize this computer on the TV, or pair using adb pair first')
        user = self.shell('am', 'get-current-user')
        if not user.isdigit():
            raise RuntimeError('Could not identify the active Android user')
        self.user = user

    def probe(self):
        sdk = self.shell('getprop', 'ro.build.version.sdk')
        if not sdk.isdigit() or int(sdk) < 26:
            raise RuntimeError('Requires Android TV / Google TV with Android 8 (API 26) or newer')
        features = self.shell('pm', 'list', 'features')
        if 'android.software.leanback' not in features and 'android.hardware.type.television' not in features:
            raise RuntimeError('The connected device does not advertise Android TV capabilities')
        return {key: self.shell('getprop', prop) for key, prop in (
            ('model', 'ro.product.model'), ('manufacturer', 'ro.product.manufacturer'), ('sdk', 'ro.build.version.sdk'))}

    def home(self):
        response = self.shell('cmd', 'package', 'resolve-activity', '--user', self.user, '--brief', '-a',
                              'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
        return next((line.strip() for line in reversed(response.splitlines()) if COMPONENT.fullmatch(line.strip())), '')

    def set_home(self, component):
        if not COMPONENT.fullmatch(component):
            raise ValueError('Invalid Home component')
        self.shell('cmd', 'package', 'set-home-activity', '--user', self.user, component)
        if self.home() != component and self.home().replace('/.', '/' + component.split('/')[0] + '.') != component.replace('/.', '/' + component.split('/')[0] + '.'):
            raise RuntimeError('Home change was not confirmed. Select the default Home in the TV settings; original launcher remains enabled.')


def build(sdk):
    if sdk is None:
        raise RuntimeError('Install Android SDK platform 34 / build-tools 34.0.0 and set ANDROID_HOME')
    env = os.environ.copy()
    env['ANDROID_HOME'] = str(sdk)
    if not env.get('JAVA_HOME'):
        java = shutil.which('java')
        if not java:
            raise RuntimeError('Install JDK 17 and set JAVA_HOME')
    wrapper = ROOT / 'launcher' / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    command = [str(wrapper), '-p', str(ROOT / 'launcher'), 'testDebugUnitTest', 'assembleDebug', '--console=plain']
    if os.name != 'nt':
        command.insert(0, 'sh')
    result = subprocess.run(command, env=env)
    if result.returncode:
        raise RuntimeError('Build/tests failed; TV has not been modified')
    return ROOT / 'launcher/app/build/outputs/apk/debug/app-debug.apk'


def snapshot(tv, info):
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    folder = ROOT / 'backup' / ('install-' + stamp)
    folder.mkdir(parents=True)
    for name, command in (('packages.txt', ('pm', 'list', 'packages')), ('disabled.txt', ('pm', 'list', 'packages', '-d')),
                          ('settings-secure.txt', ('settings', 'list', 'secure')), ('properties.txt', ('getprop',))):
        try:
            value = tv.shell(*command)
        except RuntimeError:
            if name != 'settings-secure.txt':
                raise
            value = '\n'.join(key + '=' + tv.shell('settings', 'get', 'secure', key) for key in SECURE_KEYS)
        (folder / name).write_text(value, encoding='utf-8')
    journal = dict(device=tv.serial, android_user=tv.user, info=info, previous_home=tv.home(), home_changed=False,
                   settings={}, grants=[], listener_added=False, config_changed=False,
                   previous_config=tv.command('exec-out', 'run-as', APP, 'cat', 'files/device-config.json', check=False) or None)
    save_journal(folder, journal)
    return folder, journal


def save_journal(folder, journal):
    (folder / 'restore.json').write_text(json.dumps(journal, indent=2), encoding='utf-8')


def permission_granted(package_dump, permission, user):
    sections = re.split(r'^\s*User (\d+):', package_dump, flags=re.MULTILINE)
    relevant = sections[0]
    for index in range(1, len(sections)-1, 2):
        if sections[index] == str(user):
            relevant += sections[index+1]
    return bool(re.search(re.escape(permission) + r': granted=true', relevant))


def grant(tv, folder, journal, permission):
    previous = tv.shell('dumpsys', 'package', APP)
    if permission_granted(previous, permission, tv.user):
        return
    tv.shell('pm', 'grant', '--user', tv.user, APP, permission)
    current = tv.shell('dumpsys', 'package', APP)
    if not permission_granted(current, permission, tv.user):
        raise RuntimeError('TV did not grant ' + permission + '; use native permission settings')
    journal['grants'].append(permission)
    save_journal(folder, journal)


def configure_assist(tv, folder, journal):
    grant(tv, folder, journal, 'android.permission.WRITE_SECURE_SETTINGS')
    old = tv.shell('settings', 'get', 'secure', 'enabled_accessibility_services')
    for key, value in (('enabled_accessibility_services', merged_services(old, ASSIST)), ('accessibility_enabled', '1')):
        before = tv.shell('settings', 'get', 'secure', key)
        if before == value:
            continue
        journal['settings'][key] = {'old': before, 'applied': value}
        save_journal(folder, journal)
        tv.shell('settings', 'put', 'secure', key, value)
        if tv.shell('settings', 'get', 'secure', key) != value:
            raise RuntimeError('TV did not allow accessibility setup; enable the service in native settings')
    listeners = tv.shell('settings', 'get', 'secure', 'enabled_notification_listeners')
    if LISTENER not in listeners:
        tv.shell('cmd', 'notification', 'allow_listener', LISTENER, tv.user, check=False)
        journal['listener_added'] = LISTENER in tv.shell('settings', 'get', 'secure', 'enabled_notification_listeners')
        save_journal(folder, journal)
        if not journal['listener_added']:
            print('Notification access needs enabling in the native TV settings.')


def restore(tv, folder):
    journal = json.loads((folder / 'restore.json').read_text(encoding='utf-8'))
    if journal.get('device') != tv.serial or journal.get('android_user', '0') != tv.user:
        raise ValueError('Backup belongs to a different selected device')
    previous = journal.get('previous_home', '')
    if journal.get('home_changed'):
        if not COMPONENT.fullmatch(previous) or previous.startswith('android/'):
            raise RuntimeError('Original Home was a system chooser; select it manually in the TV settings')
        tv.set_home(previous)
    for permission in journal.get('grants', []):
        if permission not in ('android.permission.WRITE_SECURE_SETTINGS', 'android.permission.ACCESS_FINE_LOCATION'):
            raise ValueError('Unknown permission in restore journal')
        tv.shell('pm', 'revoke', '--user', tv.user, APP, permission)
    if journal.get('config_changed'):
        current = tv.command('exec-out', 'run-as', APP, 'cat', 'files/device-config.json', check=False)
        if current != journal.get('applied_config'):
            print('Preserving subsequently modified device configuration')
        elif journal.get('previous_config') is None:
            tv.shell('run-as', APP, 'rm', '-f', 'files/device-config.json')
        else:
            tv.command('shell', '-T', 'run-as', APP, 'tee', 'files/device-config.json', data=journal['previous_config'].encode('utf-8'))
    if journal.get('listener_added'):
        tv.shell('cmd', 'notification', 'disallow_listener', LISTENER, tv.user, check=False)
    for key, value in journal.get('settings', {}).items():
        if key not in SECURE_KEYS:
            raise ValueError('Unknown setting in restore journal')
        if tv.shell('settings', 'get', 'secure', key) != value['applied']:
            print('Preserving subsequently modified setting:', key)
            continue
        if value['old'] == 'null':
            tv.shell('settings', 'delete', 'secure', key)
        else:
            tv.shell('settings', 'put', 'secure', key, value['old'])
    print('Restored recorded launcher/permission changes. App and profiles remain installed.')


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--device', default=os.getenv('TV_HOST'), help='Explicit ADB serial or hostname:port')
    p.add_argument('--adb', help='Path to platform-tools adb')
    p.add_argument('--sdk', help='Android SDK root')
    p.add_argument('--apk', type=Path, help='Install an already built APK instead of building')
    p.add_argument('--config', type=Path, help='Optional private device-config.json')
    p.add_argument('--probe-only', action='store_true')
    p.add_argument('--dry-run', action='store_true', help='Print plan without connecting or modifying the TV')
    p.add_argument('--set-home', action='store_true', help='Explicitly request making the app the default Home')
    p.add_argument('--enable-assist', action='store_true', help='Explicitly enable accessibility and request notification access')
    p.add_argument('--wifi-names', action='store_true', help='Grant location permission to expose Wi-Fi SSID on Android 10+')
    p.add_argument('--restore', type=Path, help='Restore recorded changes from a backup folder')
    return p


def main(argv=None):
    args = parser().parse_args(argv)
    config = validate_config(json.loads(args.config.read_text(encoding='utf-8-sig'))) if args.config else None
    if args.dry_run:
        print(json.dumps({'device': args.device, 'build_and_test': not bool(args.apk), 'install': not args.probe_only,
                          'backup_before_mutations': True, 'set_home': args.set_home, 'enable_assist': args.enable_assist,
                          'wifi_names': args.wifi_names, 'disable_packages': False, 'config_supplied': config is not None}, indent=2))
        return 0
    sdk = sdk_path(args.sdk)
    adb = args.adb or shutil.which('adb') or (str(sdk / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')) if sdk else None)
    if not adb or not Path(adb).is_file():
        raise RuntimeError('Install Android platform-tools or pass --adb')
    tv = Tv(adb, args.device)
    tv.connect()
    info = tv.probe()
    print(json.dumps(info, ensure_ascii=False))
    if args.probe_only:
        return 0
    if args.restore:
        restore(tv, args.restore.resolve())
        return 0
    apk = args.apk.resolve() if args.apk else build(sdk)
    if not apk.is_file():
        raise FileNotFoundError(apk)
    folder, journal = snapshot(tv, info)
    print('Local backup:', folder)
    result = tv.command('install', '--no-streaming', '-r', str(apk), timeout=180)
    if 'Success' not in result:
        raise RuntimeError('Install failed; original Home remains enabled. Never uninstall to bypass a signing-key mismatch.')
    if config is not None:
        journal['config_changed'] = True
        journal['applied_config'] = json.dumps(config)
        save_journal(folder, journal)
        tv.shell('run-as', APP, 'mkdir', '-p', 'files')
        tv.command('shell', '-T', 'run-as', APP, 'tee', 'files/device-config.json', data=json.dumps(config).encode('utf-8'))
    if args.enable_assist:
        configure_assist(tv, folder, journal)
    if args.wifi_names:
        grant(tv, folder, journal, 'android.permission.ACCESS_FINE_LOCATION')
    if args.set_home and not journal['previous_home'].startswith(APP + '/'):
        # Record the intended change first so interrupted setup still has a usable restore journal.
        journal['home_changed'] = True
        save_journal(folder, journal)
        tv.set_home(HOME)
    tv.shell('am', 'start', '--user', tv.user, '-n', HOME)
    print('Installed. Backup records Home/permission changes. New installations request the first individual profile on the TV.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as error:
        print('Setup stopped:', error, file=sys.stderr)
        sys.exit(1)
