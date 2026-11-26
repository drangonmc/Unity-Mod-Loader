package mc.unity.bootstrap;
import static java.lang.constant.ConstantDescs.*;

import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.*;
import java.util.function.*;
/** Version-locked game hooks. Each replacement verifies its expected number of instruction anchors. */
final class ActionPatches {
    private static final String N = "net/minecraft/", L = N + "world/level/Level",
                                SL = N + "server/level/ServerLevel", P = N + "world/entity/player/Player",
                                SP = N + "server/level/ServerPlayer", LE = N + "world/entity/LivingEntity",
                                E = N + "world/entity/Entity", SRC = N + "world/damagesource/DamageSource";
    private static final String S = N + "world/level/block/state/BlockState", POS = N + "core/BlockPos",
                                DIR = N + "core/Direction", HAND = N + "world/InteractionHand",
                                HIT = N + "world/phys/BlockHitResult", VEC = N + "world/phys/Vec3",
                                RESULT = N + "world/InteractionResult", STACK = N + "world/item/ItemStack",
                                ITEM = N + "world/item/Item", CTX = N + "world/item/context/UseOnContext",
                                CHUNK = N + "world/level/chunk/LevelChunk";
    private static final String MODE = N + "server/level/ServerPlayerGameMode",
                                CLIENT = N + "client/multiplayer/MultiPlayerGameMode",
                                LOCAL = N + "client/player/LocalPlayer",
                                PACKET = N + "network/protocol/game/ServerboundPlayerActionPacket$Action";
    private static final String ACTION = "mc/unity/core/Actions", PLAN = ACTION + "$Action",
                                PLACE = "mc/unity/core/Placement";
    private static String t(String name) {
        return "L" + name + ";";
    }
    private static void call(CodeBuilder b, String owner, String method, String sig) {
        b.invokestatic(ClassDesc.ofInternalName(owner), method, MethodTypeDesc.ofDescriptor(sig));
    }
    private static void game(CodeBuilder b, String method, String sig) {
        call(b, ACTION, method, sig);
    }
    private static void get(CodeBuilder b, String owner, String field, String type) {
        b.getfield(ClassDesc.ofInternalName(owner), field, ClassDesc.ofInternalName(type));
    }
    private static void result(CodeBuilder b) {
        var next = b.newLabel();
        b.dup().ifnull(next).areturn().labelBinding(next).pop();
    }
    private static void fail(CodeBuilder b, boolean returnsBool) {
        var ok = b.newLabel();
        b.ifeq(ok);
        if (returnsBool)
            b.iconst_0().ireturn();
        else
            b.return_();
        b.labelBinding(ok);
    }
    private interface Edit {
        boolean apply(CodeBuilder b, CodeElement e, int[] state);
    }
    private static void patch(TransformPlan plan, String cls, String method, String sig, int expected,
                              Edit edit) {
        plan.add(cls, 135, "Unity gameplay", method + sig, (cf, model) -> {
            int[] hits = {0}, state = {-1, 0, 0, 0};
            var bytes = cf.transformClass(
                model,
                ClassTransform.transformingMethodBodies(
                    m -> m.methodName().equalsString(method) && m.methodType().equalsString(sig), (b, e) -> {
                        if (edit.apply(b, e, state))
                            hits[0]++;
                        else
                            b.with(e);
                    }));
            if (expected < 0 ? hits[0] < 1 : hits[0] != expected)
                throw new IOException(cls + "." + method + " anchors: " + hits[0] + " expected " + expected);
            return bytes;
        });
    }
    private static boolean invoke(CodeElement e, String name) {
        return e instanceof InvokeInstruction i && i.name().equalsString(name);
    }
    private static void head(TransformPlan plan, String cls, String m, String sig, Consumer<CodeBuilder> fn) {
        patch(plan, cls, m, sig, 1, (b, e, k) -> {
            if (k[0] < 0 && e instanceof Instruction) {
                k[0] = 1;
                fn.accept(b);
                b.with(e);
                return true;
            }
            return false;
        });
    }
    static void add(TransformPlan plan, boolean client) {
        Consumer<CodeBuilder> suppressDrop = b -> {
            var keep = b.newLabel();
            game(b.aload(0).aload(1), "skipBlockDrop", "(" + t(L) + t(POS) + ")Z");
            b.ifeq(keep).return_().labelBinding(keep);
        };
        head(plan, N + "world/level/block/Block", "popResource", "(" + t(L) + t(POS) + t(STACK) + ")V",
             suppressDrop);
        head(plan, N + "world/level/block/Block", "popResourceFromFace",
             "(" + t(L) + t(POS) + t(N + "core/Direction") + t(STACK) + ")V", suppressDrop);
        String dmg = "(" + t(SL) + t(SRC) + "F)", hook = "(" + t(LE) + t(SRC) + "F)";
        for (String cls : List.of(P, LE)) {
            head(plan, cls, "hurtServer", dmg + "Z", b -> {
                var skip = b.newLabel();
                if (cls.equals(LE))
                    b.aload(0).instanceOf(ClassDesc.ofInternalName(P)).ifne(skip);
                game(b.aload(0).aload(2).fload(3), "attackAmount", hook + "F");
                b.fstore(3).fload(3).fconst_0().fcmpg().ifge(skip).iconst_0().ireturn();
                b.labelBinding(skip);
            });
            patch(plan, cls, "actuallyHurt", dmg + "V", 2, (b, e, k) -> {
                if (invoke(e, "getDamageAfterArmorAbsorb")) {
                    game(b, "hurt", hook + "F");
                    b.fstore(3);
                    var valid = b.newLabel();
                    b.fload(3)
                        .fconst_0()
                        .fcmpg()
                        .ifgt(valid)
                        .return_()
                        .labelBinding(valid)
                        .aload(0)
                        .aload(2)
                        .fload(3)
                        .with(e);
                    return true;
                }
                if (invoke(e, "setAbsorptionAmount") && k[0] < 0) {
                    k[0] = 1;
                    b.with(e);
                    game(b.aload(0).aload(2).fload(3), "damaged", hook + "F");
                    b.fstore(3);
                    var proceed = b.newLabel();
                    b.fload(3).fconst_0().fcmpg().ifge(proceed).return_().labelBinding(proceed);
                    return true;
                }
                return false;
            });
        }
        head(plan, P, "attack", "(" + t(E) + ")V", b -> {
            game(b.aload(0).aload(1), "attackEntity", "(" + t(P) + t(E) + ")Z");
            fail(b, false);
        });
        head(plan, P, "interactOn", "(" + t(E) + t(HAND) + t(VEC) + ")" + t(RESULT), b -> {
            game(b.aload(0).aload(1).aload(2).aload(3), "interactEntity",
                 "(" + t(P) + t(E) + t(HAND) + t(VEC) + ")" + t(RESULT));
            result(b);
        });
        for (String owner : List.of(LE, SP))
            patch(plan, owner, "die", "(" + t(SRC) + ")V", -1, (b, e, k) -> {
                if (e instanceof Instruction i && i.opcode() == Opcode.RETURN) {
                    var skip = b.newLabel();
                    if (owner.equals(LE))
                        b.aload(0).instanceOf(ClassDesc.ofInternalName(SP)).ifne(skip);
                    call(b.aload(0).aload(1), "mc/unity/core/Events", "died", "(" + t(LE) + t(SRC) + ")V");
                    b.labelBinding(skip).with(e);
                    return true;
                }
                return false;
            });
        right(plan, false);
        if (client)
            right(plan, true);
        if (client) {
            String mc = N + "client/Minecraft";
            patch(plan, mc, "startAttack", "()Z", 1, (b, e, k) -> {
                if (!invoke(e, "resetAttackStrengthTicker"))
                    return false;
                b.dup().with(e);
                game(b, "emptyLeft", "(" + t(P) + ")V");
                return true;
            });
            emptyHandUse(plan, mc);
            plan.add(
                CLIENT, 134, "Unity gameplay", "mining prediction policy",
                (cf, m)
                    -> cf.transformClass(
                        m,
                        ClassTransform.endHandler(
                            b -> b.withField("unity$denyBlockAttack", CD_boolean, ClassFile.ACC_PRIVATE))));
            patch(plan, CLIENT, "lambda$startDestroyBlock$1",
                  "(" + t(S) + t(POS) + t(DIR) + "I)" + t(N + "network/protocol/Packet"), 1, (b, e, k) -> {
                      if (!invoke(e, "attack"))
                          return false;
                      b.aload(0).getfield(ClassDesc.ofInternalName(CLIENT), "unity$denyBlockAttack",
                                          CD_boolean);
                      game(b, "clientBlockAttack", "(" + t(S) + t(L) + t(POS) + t(P) + "Z)V");
                      return true;
                  });
            for (String m : List.of("startDestroyBlock", "continueDestroyBlock"))
                head(plan, CLIENT, m, "(" + t(POS) + t(DIR) + ")Z", b -> {
                    get(b.aload(0), CLIENT, "minecraft", N + "client/Minecraft");
                    get(b, N + "client/Minecraft", "player", LOCAL);
                    b.aload(1).aload(2).ldc(m.startsWith("start") ? "START" : "CLIENT_HOLD");
                    game(b, "left", "(" + t(P) + t(POS) + t(DIR) + "Ljava/lang/String;)" + t(PLAN));
                    if (m.equals("startDestroyBlock")) {
                        b.dup().aload(0).swap();
                        game(b, "blockDenied", "(" + t(PLAN) + ")Z");
                        b.putfield(ClassDesc.ofInternalName(CLIENT), "unity$denyBlockAttack", CD_boolean);
                    }
                    game(b, "stopMining", "(" + t(PLAN) + ")Z");
                    fail(b, true);
                });
            head(plan, CLIENT, "useItem", "(" + t(P) + t(HAND) + ")" + t(RESULT), b -> {
                game(b.aload(1).aload(2), "useItem", "(" + t(P) + t(HAND) + ")" + t(RESULT));
                result(b);
            });
        }
        {
            patch(plan, MODE, "destroyBlock", "(" + t(POS) + ")Z", 2, (b, e, k) -> {
                if (invoke(e, "canDestroyBlock")) {
                    k[0] = b.allocateLocal(TypeKind.REFERENCE);
                    game(b, "breaking", "(" + t(STACK) + t(S) + t(L) + t(POS) + t(P) + ")" + t(PLAN));
                    b.dup().astore(k[0]);
                    game(b, "canBreak", "(" + t(PLAN) + ")Z");
                    return true;
                }
                if (invoke(e, "playerDestroy")) {
                    b.aload(k[0]);
                    game(b, "harvested",
                         "(" + t(N + "world/level/block/Block") + t(L) + t(P) + t(POS) + t(S) +
                             t(N + "world/level/block/entity/BlockEntity") + t(STACK) + t(PLAN) + ")V");
                    return true;
                }
                return false;
            });
            head(plan, N + "world/level/block/Block", "popExperience", "(" + t(SL) + t(POS) + "I)V", b -> {
                game(b.aload(1).aload(2), "replaceXP", "(" + t(L) + t(POS) + ")Z");
                fail(b, false);
            });
            head(plan, MODE, "useItem", "(" + t(SP) + t(L) + t(STACK) + t(HAND) + ")" + t(RESULT), b -> {
                game(b.aload(1).aload(4), "useItem", "(" + t(P) + t(HAND) + ")" + t(RESULT));
                result(b);
            });
            patch(plan, MODE, "handleBlockBreakAction", "(" + t(POS) + t(PACKET) + t(DIR) + "II)V", 3,
                  (b, e, k) -> {
                      if (k[0] < 0 && e instanceof Instruction) {
                          k[0] = b.allocateLocal(TypeKind.REFERENCE);
                          get(b.aload(0), MODE, "player", SP);
                          b.aload(1).aload(3).aload(2).invokevirtual(CD_Enum, "name",
                                                                     MethodTypeDesc.of(CD_String));
                          game(b, "leftServer",
                               "(" + t(P) + t(POS) + t(DIR) + "Ljava/lang/String;)" + t(PLAN));
                          b.dup().astore(k[0]);
                          game(b, "stopMining", "(" + t(PLAN) + ")Z");
                          fail(b, false);
                          b.with(e);
                          return true;
                      }
                      if (invoke(e, "onHitBlock")) {
                          var allow = b.newLabel();
                          var done = b.newLabel();
                          game(b.aload(k[0]), "blockDenied", "(" + t(PLAN) + ")Z");
                          b.ifeq(allow);
                          for (int i = 0; i < ((InvokeInstruction)e).typeSymbol().parameterCount(); i++)
                              b.pop();
                          b.goto_(done).labelBinding(allow).with(e).labelBinding(done);
                          return true;
                      }
                      if (invoke(e, "attack")) {
                          b.aload(k[0]);
                          game(b, "blockAttack", "(" + t(S) + t(L) + t(POS) + t(P) + t(PLAN) + ")V");
                          return true;
                      }
                      return false;
                  });
        }
        patch(plan, STACK, "useOn", "(" + t(CTX) + ")" + t(RESULT), 1, (b, e, k) -> {
            if (invoke(e, "useOn")) {
                String use = t(PLACE + "$Use");
                int slot = b.allocateLocal(TypeKind.REFERENCE);
                var start = b.newLabel();
                var end = b.newLabel();
                var fail = b.newLabel();
                var done = b.newLabel();
                call(b.dup2(), PLACE, "begin", "(" + t(ITEM) + t(CTX) + ")" + use);
                b.astore(slot).labelBinding(start).with(e).labelBinding(end).aload(slot);
                call(b, PLACE, "finish", "(" + t(RESULT) + use + ")" + t(RESULT));
                b.goto_(done).labelBinding(fail).aload(slot);
                call(b, PLACE, "abortUse", "(" + use + ")V");
                b.athrow().labelBinding(done).exceptionCatchAll(start, end, fail);
                return true;
            }
            return false;
        });
        patch(plan, L, "setBlock", "(" + t(POS) + t(S) + "II)Z", 1, (b, e, k) -> {
            if (!invoke(e, "setBlockState"))
                return false;
            call(b.iload(4), PLACE, "write", "(" + t(CHUNK) + t(POS) + t(S) + "II)" + t(S));
            var pass = b.newLabel();
            b.dup().ifnull(pass).aload(0);
            call(b, PLACE, "capturing", "(" + t(L) + ")Z");
            b.ifeq(pass).pop().iconst_1().ireturn().labelBinding(pass);
            return true;
        });
        patch(plan, CHUNK, "setBlockState", "(" + t(POS) + t(S) + "I)" + t(S), 3, (b, e, k) -> {
            if (invoke(e, "preRemoveSideEffects")) {
                call(b, PLACE, "preRemove",
                     "(" + t(N + "world/level/block/entity/BlockEntity") + t(POS) + t(S) + ")V");
                return true;
            }
            if (invoke(e, "affectNeighborsAfterRemoval")) {
                call(b, PLACE, "afterRemoval", "(" + t(S) + t(SL) + t(POS) + "Z)V");
                return true;
            }
            if (!invoke(e, "onPlace"))
                return false;
            call(b, PLACE, "onPlace", "(" + t(S) + t(L) + t(POS) + t(S) + "Z)V");
            return true;
        });
    }
    private static void emptyHandUse(TransformPlan plan, String owner) {
        plan.add(owner, 135, "Unity gameplay", "startUseItem()V", (cf, model) -> {
            var method = model.methods()
                             .stream()
                             .filter(m
                                     -> m.methodName().equalsString("startUseItem") &&
                                            m.methodType().equalsString("()V"))
                             .findFirst()
                             .orElseThrow();
            var code = method.code().orElseThrow();
            var ins = code.elementList()
                          .stream()
                          .filter(Instruction.class ::isInstance)
                          .map(Instruction.class ::cast)
                          .toList();
            int match = -1, hand = -1;
            for (int i = 0; i < ins.size(); i++) {
                if (!(ins.get(i) instanceof InvokeInstruction call) ||
                    !call.owner().asInternalName().equals(CLIENT) || !call.name().equalsString("useItem"))
                    continue;
                if (i == 0 || !(ins.get(i - 1) instanceof LoadInstruction load) ||
                    load.typeKind() != TypeKind.REFERENCE)
                    throw new IOException("Missing hand argument for useItem");
                int guard = i - 2;
                while (guard >= 0 && (ins.get(guard) instanceof LoadInstruction || ins.get(guard) instanceof
                                                                                       FieldInstruction))
                    guard--;
                if (guard < 1 || ins.get(guard).opcode() != Opcode.IFNE ||
                    !invoke(ins.get(guard - 1), "isEmpty") || match >= 0)
                    throw new IOException("Ambiguous held-stack guard before useItem");
                match = guard - 1;
                hand = load.slot();
            }
            if (match < 0)
                throw new IOException("Missing held-stack guard");
            final int target = match, handArg = hand;
            int[] index = {0};
            return cf.transformClass(
                model,
                ClassTransform.transformingMethodBodies(
                    m
                    -> m.methodName().equalsString("startUseItem") && m.methodType().equalsString("()V"),
                    (cb, el) -> {
                        if (el instanceof Instruction && index[0]++ == target) {
                            cb.dup();
                            get(cb.aload(0), owner, "player", LOCAL);
                            cb.aload(handArg);
                            get(cb.aload(0), owner, "hitResult", N + "world/phys/HitResult");
                            game(cb, "emptyRight",
                                 "(" + t(STACK) + t(P) + t(HAND) + t(N + "world/phys/HitResult") + ")V");
                        }
                        cb.with(el);
                    }));
        });
    }
    private static void right(TransformPlan plan, boolean client) {
        String cls = client ? CLIENT : MODE, method = client ? "performUseItemOn" : "useItemOn",
               sig = client ? "(" + t(LOCAL) + t(HAND) + t(HIT) + ")" + t(RESULT)
                            : "(" + t(SP) + t(L) + t(STACK) + t(HAND) + t(HIT) + ")" + t(RESULT);
        patch(plan, cls, method, sig, 4, (b, e, k) -> {
            if (k[0] < 0 && e instanceof Instruction) {
                k[0] = b.allocateLocal(TypeKind.REFERENCE);
                game(b.aload(1).aload(client ? 2 : 4).aload(client ? 3 : 5), "useBlock",
                     "(" + t(P) + t(HAND) + t(HIT) + ")" + t(PLAN));
                b.dup().astore(k[0]);
                game(b, "cancelResult", "(" + t(PLAN) + ")" + t(RESULT));
                result(b);
                b.with(e);
                return true;
            }
            if (invoke(e, "isSecondaryUseActive"))
                k[2] = 1;
            if (k[2] == 1 && e instanceof StoreInstruction i && i.typeKind() == TypeKind.INT) {
                k[2] = 2;
                game(b.aload(k[0]), "skipBlock", "(Z" + t(PLAN) + ")Z");
                b.with(e);
                return true;
            }
            if (invoke(e, "useWithoutItem"))
                k[1] = 1;
            if (k[1] > 0 && (invoke(e, "isEmpty") || invoke(e, "isOnCooldown"))) {
                b.with(e).aload(k[0]);
                game(b, "skipItem", "(Z" + t(PLAN) + ")Z");
                return true;
            }
            return false;
        });
    }
}
