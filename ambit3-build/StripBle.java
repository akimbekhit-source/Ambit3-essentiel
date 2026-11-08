import java.io.File;
import java.util.ArrayList;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;

public class StripBle {
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.forApi(28));
        int removed = 0;
        for (String name : container.getDexEntryNames()) {
            var dex = container.getEntry(name).getDexFile();
            var kept = new ArrayList<ClassDef>();
            for (var type : dex.getClasses()) {
                String desc = type.getType();
                if (desc.equals("Lcom/ambitsyncmodern/ble/AmbitBleModule;")
                    || desc.startsWith("Lcom/ambitsyncmodern/ble/AmbitBleModuleKt") || desc.startsWith("Lcom/ambitsyncmodern/ble/AmbitBleModule$")) {
                    removed++;
                } else kept.add(type);
            }
            if (kept.size() != dex.getClasses().size()) {
                DexFileFactory.writeDexFile(new File(args[1], name).getPath(),
                    new ImmutableDexFile(dex.getOpcodes(), kept));
                System.out.println(name + ": " + kept.size() + " classes retained");
            }
        }
        if (removed == 0) throw new IllegalStateException("Original BLE module not found");
        System.out.println("Removed " + removed + " original BLE classes");
    }
}
