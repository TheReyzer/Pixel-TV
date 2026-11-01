import importlib.util
from pathlib import Path
import unittest
spec=importlib.util.spec_from_file_location('source_export',Path(__file__).resolve().parents[1]/'export_source.py'); module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class ExportTest(unittest.TestCase):
    def test_only_product_source_and_required_assets_are_selected(self):
        for path in ['launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java','launcher/app/src/main/res/drawable-nodpi/avatar_animals_pixel.png','launcher/gradle/wrapper/gradle-wrapper.jar','tools/install.py','.github/workflows/android.yml','launcher/app/build.gradle']:
            self.assertTrue(module.selected(Path(path)),path)
        for path in ['backup/profiles.xml','tools/adbkey/adbkey','tools/venv/private.py','launcher/screen.png','launcher/local.properties','.git/config','dist/private.java','examples/tcl-beyondtv/private.md','launcher/app/build/intermediates/private.java','launcher/profile-assets/raw.png','launcher/app/src/main/assets/secret.jks','launcher/app/src/main/assets/device-config.json','launcher/app/src/main/assets/device-config.local.json','launcher/app/src/main/assets/viewer_profiles_v1.xml']:
            self.assertFalse(module.selected(Path(path)),path)
