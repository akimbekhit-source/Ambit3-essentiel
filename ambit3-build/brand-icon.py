"""Draw the by A.B. monogram for the Android launcher's existing density resources."""
from PIL import Image, ImageDraw
from io import BytesIO
from zipfile import ZipFile
from pathlib import Path
import sys

paths = {
    'res/QZ.png', 'res/zr.png', 'res/Em.png', 'res/Lf.png', 'res/as.png',
    'res/9w.png', 'res/yn.png', 'res/FS.png', 'res/RJ.png', 'res/o-.png',
    'res/zR.png', 'res/8c.png', 'res/wb.png', 'res/fO.png', 'res/Gc.png',
}
adaptive = {'res/QZ.png', 'res/zr.png', 'res/Em.png', 'res/Lf.png', 'res/as.png'}
with ZipFile(sys.argv[1]) as original, ZipFile(sys.argv[2], 'w') as updated:
    assert paths <= set(original.namelist()), 'Missing launcher resources'
    for item in original.infolist():
        data = original.read(item)
        if item.filename in paths:
            width, height = Image.open(BytesIO(data)).size
            image = Image.new('RGB', (640, 640), '#102633')
            draw = ImageDraw.Draw(image)
            scale = 3.8 if item.filename in adaptive else 6.0
            def points(coords):
                return [(320 + (x-50)*scale, 320 + (y-50)*scale) for x, y in coords]
            draw.line(points([(25,76),(50,24),(75,76)]), fill='#F2FAFF', width=round(6*scale), joint='curve')
            draw.line(points([(20,59),(41,59),(48,49),(54,67),(61,59),(80,59)]), fill='#53E2BA', width=round(5*scale), joint='curve')
            output = BytesIO()
            image.resize((width, height), Image.Resampling.LANCZOS).save(output, format='PNG')
            data = output.getvalue()
        updated.writestr(item, data)
