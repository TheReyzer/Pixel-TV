#!/usr/bin/env python3
"""Create a clean source-only folder/ZIP without Git history or local device data."""
import argparse
import json
from pathlib import Path
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ROOT_FILES = {'README.md','PROMPT.md','AGENTS.md','CONTRIBUTING.md','LICENSE','PRIVACY.md','SECURITY.md','.gitignore'}
TOOLS = {'install.py','export_source.py'}
PRIVATE_TEXT = (re.compile(r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----'),
                re.compile(r'(?i)C:[/\\]Users[/\\]'), re.compile(r'(?i)(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}'))


def selected(path):
    parts=path.parts
    if path.name in {'adbkey','device-config.json','viewer_profiles_v1.xml','restore.json'} or '.local.' in path.name or path.suffix in {'.keystore','.jks','.pem','.p12','.apk','.aab','.pyc'}: return False
    if any(part.startswith('.') and part not in ('.github','.gitignore') for part in parts): return False
    if len(parts)==1: return path.name in ROOT_FILES
    if parts[0]=='.github': return path.suffix in ('.yml','.yaml','.md')
    if parts[0]=='docs': return path.suffix=='.md' or path.name=='device-config.example.json'
    if parts[0]=='tools': return len(parts)==2 and path.name in TOOLS or len(parts)==3 and parts[1]=='tests' and path.suffix=='.py'
    if parts[0]=='launcher':
        if parts[1:4]==('app','src','main') or parts[1:4]==('app','src','test'): return True
        if parts[1:3]==('gradle','wrapper'): return True
        if len(parts)==2: return path.name in {'build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','build-and-install.sh','build-and-install.ps1','THIRD_PARTY_NOTICES.md','COPYING','LICENSE'}
        return len(parts)==3 and path.parts[1] in ('profile-assets','discovery-assets') and path.name=='README.md' or path==Path('launcher/app/build.gradle')
    return False


def export(destination):
    destination=destination.resolve()
    if destination==ROOT or ROOT.is_relative_to(destination): raise ValueError('Choose a new output folder outside the source ancestors')
    if destination.exists(): raise ValueError('Output already exists; choose a new folder')
    files=[]
    for path in ROOT.rglob('*'):
        if path.is_symlink() or not path.is_file(): continue
        relative=path.relative_to(ROOT)
        if not selected(relative): continue
        if path.suffix in ('.java','.xml','.py','.md','.json','.gradle','.yml','.yaml','.ps1','.sh'):
            text=path.read_text(encoding='utf-8-sig')
            if relative!=Path('tools/export_source.py') and any(pattern.search(text) for pattern in PRIVATE_TEXT):
                raise ValueError('Private-looking content in '+str(relative)+'; review before publication')
        files.append((path,relative))
    for source,relative in files:
        target=destination/relative; target.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(source,target)
    return files


def main():
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--output',type=Path,required=True); parser.add_argument('--zip',action='store_true'); args=parser.parse_args()
    files=export(args.output)
    if args.zip:
        with zipfile.ZipFile(args.output.with_suffix('.zip'),'x',zipfile.ZIP_DEFLATED) as archive:
            for _,relative in files: archive.write(args.output/relative,str(Path(args.output.name)/relative))
    print(json.dumps({'folder':str(args.output.resolve()),'files':len(files),'history_included':False,'device_backups_included':False},indent=2))


if __name__=='__main__': main()
