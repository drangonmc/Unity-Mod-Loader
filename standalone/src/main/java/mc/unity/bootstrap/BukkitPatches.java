package mc.unity.bootstrap;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;

final class BukkitPatches {
    private static final String PLAYER = "net/minecraft/server/level/ServerPlayer",
                                LIST = "net/minecraft/server/players/PlayerList",
                                GAME = "net/minecraft/server/network/ServerGamePacketListenerImpl",
                                MSG = "net/minecraft/network/chat/Component";
    private static final ClassDesc HOOK = ClassDesc.of("mc.unity.runtime.ServerHooks");
    interface Edit {
        boolean apply(CodeBuilder b, CodeElement e);
    }
    static void method(TransformPlan plan, String owner, String method, String anchor, Edit edit) {
        method(plan,owner,MethodTarget.named(method),anchor,edit);
    }
    static void method(TransformPlan plan,String owner,MethodTarget target,String anchor,Edit edit) {
        plan.add(owner, 210, "Bukkit events", target.origin() + " " + anchor, (cf, model) -> {
            String method=target.resolve(model);
            int[] hits = {0};
            byte[] bytes = cf.transformClass(model, ClassTransform.transformingMethodBodies(
                                                        m -> m.methodName().equalsString(method), (b, e) -> {
                                                            if (edit.apply(b, e))
                                                                hits[0]++;
                                                            else
                                                                b.with(e);
                                                        }));
            if (hits[0] != 1)
                throw new IOException("Unexpected Bukkit hook count " + owner + "." + method + " " + anchor +
                                      " " + hits[0]);
            return bytes;
        });
    }
    static void add(TransformPlan plan) {
        String callbacks="net/minecraft/server/level/ServerLevel$EntityCallbacks";
        String sig="(Lnet/minecraft/world/entity/Entity;)V";
        for (var name:java.util.List.of("onTrackingEnd","onDestroyed")) {
            plan.add(callbacks,210,"Bukkit metadata",name,(cf,model)->{
                int[] hits={0};
                var bytes=cf.transformClass(model,ClassTransform.transformingMethodBodies(
                    m->m.methodName().equalsString(name) && m.methodType().equalsString(sig),(b,e)->{
                        if(e instanceof ReturnInstruction){hits[0]++;b.aload(1).invokestatic(HOOK,"entityUntracked",MethodTypeDesc.ofDescriptor(sig));}
                        b.with(e);
                    }));
                if(hits[0]!=1)throw new IOException("Changed entity lifecycle "+name);
                return bytes;
            });
        }
        join(plan);
        quit(plan);
        acceptedDamage(plan);
        ProtectionPatches.add(plan);
        playerHooks(plan);
        method(plan, GAME, "handleContainerClick", "suppressRemoteUpdates", (b, e) -> {
            if (!(e instanceof InvokeInstruction i) || !i.name().equalsString("suppressRemoteUpdates"))
                return false;
            var proceed = b.newLabel();
            b.aload(0).getfield(ClassDesc.ofInternalName(GAME), "player", ClassDesc.ofInternalName(PLAYER))
                .aload(1).invokestatic(HOOK, "containerInput", MethodTypeDesc.ofDescriptor(
                    "(L" + PLAYER + ";Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;)Z"))
                .ifeq(proceed).pop().return_().labelBinding(proceed).with(e);
            return true;
        });
        String chunk = "net/minecraft/world/level/chunk/LevelChunk";
        var chunkType = ClassDesc.ofInternalName(chunk);
        plan.add(chunk, 211, "Bukkit chunk generation", "new chunk identity", (cf, model) -> {
            int[] hits = {0};
            var out = cf.transformClass(
                model,
                ClassTransform
                    .transformingMethodBodies(m
                                              -> m.methodName().equalsString("<init>") &&
                                                     m.methodType().stringValue().contains(
                                                         "Lnet/minecraft/world/level/chunk/ProtoChunk;"),
                                              (b, e) -> {
                                                  if (e instanceof ReturnInstruction) {
                                                      hits[0]++;
                                                      b.aload(0).iconst_1().putfield(
                                                          chunkType, "unity$newChunk",
                                                          ConstantDescs.CD_boolean);
                                                  }
                                                  b.with(e);
                                              })
                    .andThen(ClassTransform.endHandler(
                        b
                        -> b.withField("unity$newChunk", ConstantDescs.CD_boolean, ClassFile.ACC_PRIVATE))));
            if (hits[0] != 1)
                throw new IOException("Missing new chunk constructor");
            return out;
        });
        method(plan, chunk, "registerAllBlockEntitiesAfterLevelLoad", "ready", (b, e) -> {
            if (!(e instanceof ReturnInstruction))
                return false;
            b.aload(0)
                .aload(0)
                .getfield(chunkType, "unity$newChunk", ConstantDescs.CD_boolean)
                .invokestatic(HOOK, "chunkLoad", MethodTypeDesc.ofDescriptor("(L" + chunk + ";Z)V"))
                .aload(0)
                .iconst_0()
                .putfield(chunkType, "unity$newChunk", ConstantDescs.CD_boolean)
                .with(e);
            return true;
        });
        method(plan, "net/minecraft/server/level/ServerLevel", "save", "saveLevelData", (b, e) -> {
            if (!(e instanceof InvokeInstruction i) || !i.name().equalsString("saveLevelData"))
                return false;
            b.aload(0)
                .invokestatic(HOOK, "worldSave",
                              MethodTypeDesc.ofDescriptor("(Lnet/minecraft/server/level/ServerLevel;)V"))
                .with(e);
            return true;
        });
        method(plan, "net/minecraft/server/level/ChunkMap", MethodTarget.callback("scheduleUnload","save"), "save", (b, e) -> {
            if (!(e instanceof InvokeInstruction i) || !i.name().equalsString("save"))
                return false;
            var save = b.newLabel();
            var end = b.newLabel();
            b.dup()
                .invokestatic(HOOK, "saveUnloadingChunk",
                              MethodTypeDesc.ofDescriptor("(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z"))
                .ifne(save)
                .pop2()
                .iconst_0()
                .goto_(end)
                .labelBinding(save)
                .with(e)
                .labelBinding(end);
            return true;
        });
        // The first getUsedItemHand check is inside the valid-slot branch and before vanilla interrupts
        // active use.
        method(plan, GAME, "handleSetCarriedItem", "getUsedItemHand", (b, e) -> {
            if (!(e instanceof InvokeInstruction i) || !i.name().equalsString("getUsedItemHand"))
                return false;
            var go = b.newLabel();
            b.dup()
                .aload(1)
                .invokevirtual(
                    ClassDesc.of("net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket"),
                    "getSlot", MethodTypeDesc.ofDescriptor("()I"))
                .invokestatic(HOOK, "changeSlot", MethodTypeDesc.ofDescriptor("(L" + PLAYER + ";I)Z"))
                .ifne(go)
                .pop()
                .return_()
                .labelBinding(go)
                .with(e);
            return true;
        });
    }
    private static void playerHooks(TransformPlan plan) {
        String mode = "net/minecraft/server/level/ServerPlayerGameMode",
               gameType = "net/minecraft/world/level/GameType";
        plan.add(mode, 211, "Bukkit player game mode", "changeGameModeForPlayer",
                 (cf, model)
                     -> cf.transformClass(
                         model, ClassTransform.transformingMethodBodies(
                                    m
                                    -> m.methodName().equalsString("changeGameModeForPlayer") &&
                                           m.methodType().equalsString("(L" + gameType + ";)Z"),
                                    new CodeTransform() {
                                        public void atStart(CodeBuilder b) {
                                            var keep = b.newLabel();
                                            b.aload(0)
                                                .getfield(ClassDesc.ofInternalName(mode), "player",
                                                          ClassDesc.ofInternalName(PLAYER))
                                                .aload(1)
                                                .invokestatic(HOOK, "allowMode",
                                                              MethodTypeDesc.ofDescriptor(
                                                                  "(L" + PLAYER + ";L" + gameType + ";)Z"))
                                                .ifne(keep)
                                                .iconst_0()
                                                .ireturn()
                                                .labelBinding(keep);
                                        }
                                        public void accept(CodeBuilder b, CodeElement e) {
                                            b.with(e);
                                        }
                                    })));
        // Observe final level changes once per player tick, including enchanting and direct command writes.
        plan.add(
            PLAYER, 211, "Bukkit experience levels", "doTick",
            (cf, model)
                -> cf.transformClass(
                    model, ClassTransform.transformingMethodBodies(
                               m
                               -> m.methodName().equalsString("doTick") && m.methodType().equalsString("()V"),
                               (b, e) -> {
                                   if (e instanceof ReturnInstruction)
                                       b.aload(0).invokestatic(
                                           HOOK, "levelTick",
                                           MethodTypeDesc.ofDescriptor("(L" + PLAYER + ";)V"));
                                   b.with(e);
                               })));
        method(plan, GAME, "handlePlayerAbilities", "flying", (b, e) -> {
            if (!(e instanceof FieldInstruction field) || field.opcode() != Opcode.PUTFIELD ||
                !field.name().equalsString("flying"))
                return false;
            var keep = b.newLabel();
            var end = b.newLabel();
            b.dup()
                .aload(0)
                .getfield(ClassDesc.ofInternalName(GAME), "player", ClassDesc.ofInternalName(PLAYER))
                .invokestatic(HOOK, "allowFlight", MethodTypeDesc.ofDescriptor("(ZL" + PLAYER + ";)Z"))
                .ifne(keep)
                .pop2()
                .goto_(end)
                .labelBinding(keep)
                .with(e)
                .labelBinding(end);
            return true;
        });
    }
    private static final MethodTypeDesc MESSAGE = MethodTypeDesc.ofDescriptor(
        "(L"+MSG+";L"+PLAYER+";Z)L"+MSG+";");

    private static void join(TransformPlan plan) {
        plan.add(LIST,210,"Bukkit events","placeNewPlayer registered join",(cf,model)->{
            int[] hits={0,0};
            var out=cf.transformClass(model,ClassTransform.transformingMethodBodies(
                m->m.methodName().equalsString("placeNewPlayer"),new CodeTransform() {
                    int text,overlay;
                    public void atStart(CodeBuilder b) {
                        text=b.allocateLocal(TypeKind.REFERENCE); overlay=b.allocateLocal(TypeKind.INT);
                        b.aconst_null().astore(text).iconst_0().istore(overlay);
                    }
                    public void accept(CodeBuilder b,CodeElement e) {
                        if (e instanceof InvokeInstruction i && i.owner().asInternalName().equals(LIST)
                            && i.name().equalsString("broadcastSystemMessage") && i.type().equalsString("(L"+MSG+";Z)V")) {
                            hits[0]++;
                            var delayed=b.newLabel();
                            b.istore(overlay).astore(text).pop()
                                .invokestatic(HOOK,"deferJoin",MethodTypeDesc.of(ConstantDescs.CD_boolean)).ifne(delayed)
                                .aload(0).aload(text).iload(overlay).with(e).aconst_null().astore(text).labelBinding(delayed);
                            return;
                        }
                        b.with(e);
                        if (e instanceof InvokeInstruction i && i.owner().asInternalName().equals("net/minecraft/server/level/ServerLevel")
                            && i.name().equalsString("addNewPlayer")) {
                            hits[1]++;
                            var done=b.newLabel();
                            b.aload(text).ifnull(done).aload(text).aload(2).iconst_1().invokestatic(HOOK,"playerMessage",MESSAGE)
                                .astore(text).aload(text).ifnull(done)
                                .aload(0).aload(text).iload(overlay).invokevirtual(ClassDesc.ofInternalName(LIST),
                                     "broadcastSystemMessage",MethodTypeDesc.ofDescriptor("(L"+MSG+";Z)V")).labelBinding(done);
                        }
                    }
                }));
            if(hits[0]!=1 || hits[1]!=1) throw new IOException("Changed join broadcast or world admission anchors");
            return out;
        });
    }
    private static void acceptedDamage(TransformPlan plan) {
        String name="net/minecraft/world/entity/LivingEntity";
        plan.add(name,210,"Bukkit events","hurtServer accepted damage branches",(cf,m)->{
            int[] hits={0,0};
            var bytes=cf.transformClass(m,ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("hurtServer"),
                new CodeTransform() {
                    Label excess, fresh;
                    boolean previous, bypass;
                    public void accept(CodeBuilder b,CodeElement e) {
                        if(e instanceof FieldInstruction f) {
                            if(f.opcode()==Opcode.GETFIELD && f.name().equalsString("lastHurt")) previous=true;
                            if(f.opcode()==Opcode.GETSTATIC && f.name().equalsString("BYPASSES_COOLDOWN")) bypass=true;
                        }
                        if(e instanceof BranchInstruction branch) {
                            if(previous && branch.opcode()==Opcode.IFGT && excess==null) excess=branch.target();
                            if(bypass && branch.opcode()==Opcode.IFNE && fresh==null) fresh=branch.target();
                            previous=false; bypass=false;
                        }
                        b.with(e);
                        if(e instanceof LabelTarget target && (target.label()==excess || target.label()==fresh)) {
                            boolean delta=target.label()==excess;
                            hits[delta?0:1]++;
                            b.aload(0).aload(2).fload(3);
                            if(delta)b.aload(0).getfield(ClassDesc.ofInternalName(name),"lastHurt",ConstantDescs.CD_float);
                            else b.fconst_0();
                            var ok=b.newLabel();
                            b.invokestatic(HOOK,"acceptedDamage",MethodTypeDesc.ofDescriptor("(L"+name+";Lnet/minecraft/world/damagesource/DamageSource;FF)F"))
                                .fstore(3).fload(3).fconst_0().fcmpg().ifge(ok).iconst_0().ireturn().labelBinding(ok);
                        }
                    }
                }));
            if(hits[0]!=1 || hits[1]!=1) throw new IOException("Changed vanilla damage cooldown decisions");
            return bytes;
        });
    }
    private static void quit(TransformPlan plan) {
        method(plan,GAME,"removePlayerFromWorld","broadcastSystemMessage",(b,e)->{
            if (!(e instanceof InvokeInstruction i) || !i.name().equalsString("broadcastSystemMessage")
                || !i.type().equalsString("(L"+MSG+";Z)V")) return false;
            int overlay=b.allocateLocal(TypeKind.INT);
            var send=b.newLabel(); var end=b.newLabel();
            b.istore(overlay).aload(0).getfield(ClassDesc.ofInternalName(GAME),"player",ClassDesc.ofInternalName(PLAYER))
                .iconst_0().invokestatic(HOOK,"playerMessage",MESSAGE).dup().ifnonnull(send)
                .pop2().goto_(end).labelBinding(send).iload(overlay).with(e).labelBinding(end);
            return true;
        });
    }
}
