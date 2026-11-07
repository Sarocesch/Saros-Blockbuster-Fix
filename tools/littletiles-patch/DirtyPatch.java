import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;

/** Ruft SaroDirty.mark(this) in LittleStructure.updateStructure und LittleDoorActivator.openDoor auf. */
public class DirtyPatch {
    static final String HELPER = "com/creativemd/littletiles/SaroDirty";
    static final String DESC = "(Lcom/creativemd/littletiles/common/structure/LittleStructure;)V";

    public static void main(String[] a) throws Exception {
        patch(a[0], a[1], "updateStructure", true);
        patch(a[2], a[3], "openDoor", false);
    }

    static void patch(String in, String out, String method, boolean afterQueue) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(in))).accept(cn, 0);
        int n = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals(method)) continue;
            InsnList call = new InsnList();
            call.add(new VarInsnNode(Opcodes.ALOAD, 0));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "mark", DESC, false));
            if (afterQueue) {
                for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                    if (i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("queueStructureForUpdatePacket")) {
                        m.instructions.insert(i, call);
                        n++;
                        break;
                    }
                }
            } else {
                m.instructions.insert(call);
                n++;
            }
        }
        if (n != 1) throw new IllegalStateException(method + " patched=" + n);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.createDirectories(Paths.get(out).getParent());
        Files.write(Paths.get(out), cw.toByteArray());
        System.out.println("ok " + method);
    }
}
