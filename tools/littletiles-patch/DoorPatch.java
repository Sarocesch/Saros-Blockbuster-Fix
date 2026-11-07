import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;

/** LittleDoorBase.openDoor: new Placement(player, ...) -> new Placement(null, ...). */
public class DoorPatch {
    public static void main(String[] a) throws Exception {
        byte[] in = Files.readAllBytes(Paths.get(a[0]));
        ClassNode cn = new ClassNode();
        new ClassReader(in).accept(cn, 0);
        int patched = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("openDoor")) continue;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n.getOpcode() == Opcodes.NEW && ((TypeInsnNode) n).desc.endsWith("/Placement")) {
                    AbstractInsnNode dup = n.getNext();
                    AbstractInsnNode load = dup.getNext();
                    if (dup.getOpcode() == Opcodes.DUP && load.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) load).var == 1) {
                        m.instructions.set(load, new InsnNode(Opcodes.ACONST_NULL));
                        patched++;
                    }
                }
            }
        }
        if (patched != 1) throw new IllegalStateException("patched=" + patched);
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        Files.write(Paths.get(a[1]), cw.toByteArray());
        System.out.println("ok patched=" + patched);
    }
}
