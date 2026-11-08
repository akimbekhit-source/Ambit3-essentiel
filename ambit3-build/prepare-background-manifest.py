"""Patch an apktool-decoded v0.2.59 manifest for the isolated Ambit3 application."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
root = Path(sys.argv[1])
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
a = '{http://schemas.android.com/apk/res/android}'
p = root / 'AndroidManifest.xml'
tree = ET.parse(p)
m = tree.getroot()
m.set('package', 'com.ambitsportlocal')
for node in m.iter():
    for key, value in list(node.attrib.items()):
        if value.startswith('com.ambitsyncmodern.') and (key == a+'authorities' or 'DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' in value):
            node.set(key, value.replace('com.ambitsyncmodern', 'com.ambitsportlocal'))
        if key == a+'scheme' and value == 'opensportsync': node.set(key, 'ambit3prosync')
for node in m.findall('uses-permission'):
    if node.get(a+'name') == 'android.permission.ACCESS_FINE_LOCATION':
        node.attrib.pop(a+'maxSdkVersion', None)
for permission in ['FOREGROUND_SERVICE','FOREGROUND_SERVICE_CONNECTED_DEVICE','POST_NOTIFICATIONS','WAKE_LOCK','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','ACCESS_BACKGROUND_LOCATION','FOREGROUND_SERVICE_LOCATION']:
    name = 'android.permission.' + permission
    if not any(n.get(a+'name') == name for n in m.findall('uses-permission')):
        m.insert(0, ET.Element('uses-permission', {a+'name': name}))
app = m.find('application')
assert app is not None
ET.SubElement(app, 'service', {a+'name': 'com.ambitsportlocal.background.SyncService', a+'exported': 'false', a+'foregroundServiceType': 'connectedDevice|location', a+'stopWithTask': 'false'})
activity = ET.SubElement(app, 'activity', {a+'name': 'com.ambitsportlocal.background.SyncControlActivity', a+'exported': 'true', a+'permission': 'com.ambitsportlocal.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION', a+'excludeFromRecents': 'true', a+'theme': '@android:style/Theme.Translucent.NoTitleBar'})
f = ET.SubElement(activity, 'intent-filter')
ET.SubElement(f, 'action', {a+'name': 'com.ambitsportlocal.SYNC_CONTROL'})
ET.SubElement(f, 'category', {a+'name': 'android.intent.category.DEFAULT'})
tree.write(p, encoding='utf-8', xml_declaration=True)
p=root/'apktool.yml'
s=p.read_text().replace('versionCode: 259','versionCode: 260').replace('versionName: 0.2.59','versionName: 0.2.60')
p.write_text(s)
