import contextlib
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch, Mock
import tempfile

spec = importlib.util.spec_from_file_location('installer', Path(__file__).resolve().parents[1] / 'install.py')
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class InstallTest(unittest.TestCase):
    def test_config_rejects_mistakes_and_command_injection(self):
        for value in ({'liveTvPackage': 'bad; rm'}, {'weather': {'latitude': 91, 'longitude': 0}},
                      {'weather': {'latitude': float('nan'), 'longitude': 0}}, {'profiles': ['private']},
                      {'hiddenApps': ['bad app']}, {'weather': {'latitude': True, 'longitude': 1}}):
            with self.assertRaises(ValueError):
                installer.validate_config(value)
        self.assertEqual({}, installer.validate_config({}))

    def test_assist_preserves_other_accessibility_services(self):
        self.assertEqual('other/app:ours/service', installer.merged_services('other/app', 'ours/service'))
        self.assertEqual('ours/service', installer.merged_services('null', 'ours/service'))
        self.assertEqual('ours/service', installer.merged_services('ours/service', 'ours/service'))

    def test_dry_run_is_offline_and_never_disables_packages(self):
        with patch.object(installer, 'run', side_effect=AssertionError('must not contact device')):
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(0, installer.main(['--dry-run', '--device', 'example:5555']))
        plan = json.loads(output.getvalue())
        self.assertFalse(plan['disable_packages'])
        self.assertFalse(plan['set_home'])
        self.assertFalse(plan['enable_assist'])

    def test_shell_arguments_are_quoted_as_arguments(self):
        tv = installer.Tv('adb', 'example:5555')
        with patch.object(installer, 'run', return_value='ok') as run:
            tv.shell('settings', 'put', 'secure', 'key', 'value; unsafe')
            self.assertEqual("settings put secure key 'value; unsafe'", run.call_args.args[0][-1])
        with self.assertRaises(ValueError):
            installer.Tv('adb', 'host; unsafe')

    def test_install_failure_never_changes_home_or_disables_apps(self):
        with tempfile.TemporaryDirectory() as directory:
            folder=Path(directory); adb=folder/'adb'; apk=folder/'app.apk'; adb.touch(); apk.touch()
            tv=Mock(); tv.probe.return_value={'sdk':'28'}; tv.command.side_effect=RuntimeError('INSTALL_FAILED_UPDATE_INCOMPATIBLE')
            with patch.object(installer,'Tv',return_value=tv), patch.object(installer,'snapshot',return_value=(folder,{})), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(RuntimeError,'INCOMPATIBLE'):
                    installer.main(['--adb',str(adb),'--apk',str(apk),'--device','test:5555','--set-home'])
            tv.set_home.assert_not_called(); tv.shell.assert_not_called()

    def test_restore_does_not_overwrite_later_user_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            folder=Path(directory)
            journal={'device':'test:5555','android_user':'0','home_changed':False,'grants':[],'listener_added':False,
                     'settings':{'accessibility_enabled':{'old':'0','applied':'1'}},
                     'config_changed':True,'previous_config':None,'applied_config':'old installer config'}
            (folder/'restore.json').write_text(json.dumps(journal),encoding='utf-8')
            tv=Mock();tv.serial='test:5555';tv.user='0';tv.shell.return_value='user choice';tv.command.return_value='user config'
            with contextlib.redirect_stdout(io.StringIO()): installer.restore(tv,folder)
            self.assertTrue(all('put' not in c.args and 'rm' not in c.args for c in tv.shell.call_args_list))
            self.assertTrue(all('tee' not in c.args for c in tv.command.call_args_list));tv.set_home.assert_not_called()
            tv.user='10'
            with self.assertRaises(ValueError): installer.restore(tv,folder)

    def test_active_android_user_is_used_for_settings_install_and_private_files(self):
        tv=installer.Tv('adb','test:5555');tv.user='10'
        with patch.object(installer,'run',return_value='ok') as run:
            tv.command('install','-r','app.apk');self.assertIn('--user',run.call_args.args[0]);self.assertIn('10',run.call_args.args[0])
            tv.command('exec-out','run-as',installer.APP,'cat','files/device-config.json');self.assertIn('10',run.call_args.args[0])
            tv.shell('settings','get','secure','key');self.assertIn('--user 10',run.call_args.args[0][-1])

    def test_permission_status_is_specific_to_the_active_android_user(self):
        dump='install permissions:\n android.permission.WRITE_SECURE_SETTINGS: granted=true\n User 0: installed=true\n android.permission.ACCESS_FINE_LOCATION: granted=true\n User 10: installed=true\n android.permission.ACCESS_FINE_LOCATION: granted=false\n'
        self.assertTrue(installer.permission_granted(dump,'android.permission.WRITE_SECURE_SETTINGS','10'))
        self.assertTrue(installer.permission_granted(dump,'android.permission.ACCESS_FINE_LOCATION','0'))
        self.assertFalse(installer.permission_granted(dump,'android.permission.ACCESS_FINE_LOCATION','10'))


if __name__ == '__main__':
    unittest.main()
