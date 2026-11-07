import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;

/** EntityAnimation.readEntityFromNBT: fakeWorld.getTileEntity(pos) -> SaroDirty.getOrCreateTe(world, pos). */
public class TePatch {
    public static void main(String[] a) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(a[0]))).accept(cn, 0);
        int n = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("func_70037_a")) continue;
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("func_175625_s")
                        && i.getNext() instanceof TypeInsnNode && ((TypeInsnNode) i.getNext()).desc.endsWith("TileEntityLittleTiles")) {
                    m.instructions.set(i, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/creativemd/littletiles/SaroDirty", "getOrCreateTe",
                        "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/tileentity/TileEntity;", false));
                    n++;
                    break;
                }
            }
        }
        if (n != 1) throw new IllegalStateException("patched=" + n);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.createDirectories(Paths.get(a[1]).getParent());
        Files.write(Paths.get(a[1]), cw.toByteArray());
        System.out.println("ok");
    }
}
