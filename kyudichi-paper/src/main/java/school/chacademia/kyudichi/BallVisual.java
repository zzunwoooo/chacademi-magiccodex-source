package school.chacademia.kyudichi;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * What a ball looks like. The match only moves a position; this follows it.
 * ModelEngine (reflection, same calls as ChacaGimmick ModelEngineRenderer) when the blueprint exists, else an item display.
 * Every spawned entity is tagged and non-persistent so a crash never leaves balls in the world.
 */
interface BallVisual {
    String TAG = "chacademi_kyudichi";

    void move(Location to);
    void animate(String animation);
    void remove();

    static BallVisual spawn(Location at, BallKind kind, Rules rules, Logger log) {
        BallVisual v = ModelEngineVisual.spawn(at, rules.models.get(kind), rules.modelScale, log);
        // the snitch never glows (owner 2026-10-17: no way to see it through walls)
        return v != null ? v : new ItemVisual(at, new ItemStack(rules.fallback.get(kind)), kind == BallKind.SNITCH ? 0.7f : 1.0f, kind != BallKind.SNITCH);
    }

    static void prepare(Entity e) {
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
        e.setSilent(true);
        e.setInvulnerable(true);
    }

    final class ItemVisual implements BallVisual {
        private final ItemDisplay display;
        ItemVisual(Location at, ItemStack item, float scale, boolean glow) {
            display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                prepare(d);
                d.setItemStack(item);
                d.setBillboard(Display.Billboard.CENTER);
                d.setTeleportDuration(1);
                d.setViewRange(4f);
                d.setGlowing(glow);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            });
        }
        public void move(Location to) { if (display.isValid()) display.teleport(to); }
        public void animate(String animation) { }
        public void remove() { display.remove(); }
    }

    final class ModelEngineVisual implements BallVisual {
        private static boolean resolved, ok, warned;
        private static Method createActive, createModeled, removeModeled, setBaseVisible, setScale, addModel, destroy, animHandler, play;

        private final ArmorStand base; private final Object modeled, model;

        private ModelEngineVisual(ArmorStand base, Object modeled, Object model) { this.base = base; this.modeled = modeled; this.model = model; }

        static synchronized boolean resolve(Logger log) {
            if (resolved) return ok;
            resolved = true;
            var me = Bukkit.getPluginManager().getPlugin("ModelEngine");
            if (me == null || !me.isEnabled()) return false;
            try {
                ClassLoader cl = me.getClass().getClassLoader();
                Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI", true, cl);
                Class<?> active = Class.forName("com.ticxo.modelengine.api.model.ActiveModel", true, cl);
                Class<?> modeledT = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity", true, cl);
                createActive = api.getMethod("createActiveModel", String.class);
                createModeled = api.getMethod("createModeledEntity", Entity.class);
                removeModeled = api.getMethod("removeModeledEntity", UUID.class);
                setBaseVisible = modeledT.getMethod("setBaseEntityVisible", boolean.class);
                addModel = modeledT.getMethod("addModel", active, boolean.class);
                destroy = modeledT.getMethod("destroy");
                setScale = active.getMethod("setScale", double.class);
                animHandler = active.getMethod("getAnimationHandler");
                play = animHandler.getReturnType().getMethod("playAnimation", String.class, double.class, double.class, double.class, boolean.class);
                ok = true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                log.warning("ModelEngine 연결 실패, 아이템 모양으로 표시: " + e);
            }
            return ok;
        }

        static void reset() { resolved = false; ok = false; warned = false; }

        static BallVisual spawn(Location at, String modelId, double scale, Logger log) {
            if (!resolve(log)) return null;
            Object model;
            try { model = createActive.invoke(null, modelId); } catch (ReflectiveOperationException | RuntimeException e) { model = null; }
            if (model == null) {
                if (!warned) { warned = true; log.warning("ModelEngine 모델 " + modelId + " 없음 → /meg reload 후 다시 시작하세요. 지금은 아이템 모양으로 표시"); }
                return null;
            }
            ArmorStand base = at.getWorld().spawn(at, ArmorStand.class, a -> {
                prepare(a); a.setVisible(false); a.setMarker(true); a.setGravity(false); a.setSmall(true);
            });
            try {
                Object modeled = createModeled.invoke(null, base);
                setBaseVisible.invoke(modeled, false);
                setScale.invoke(model, scale);
                addModel.invoke(modeled, model, true);
                var v = new ModelEngineVisual(base, modeled, model);
                v.animate("idle");
                return v;
            } catch (ReflectiveOperationException | RuntimeException e) {
                base.remove();
                log.warning("ModelEngine 모델 소환 실패 " + modelId + ": " + e);
                return null;
            }
        }

        public void move(Location to) { if (base.isValid()) base.teleport(to); }
        public void animate(String animation) {
            try { play.invoke(animHandler.invoke(model), animation, 0.1, 0.1, 1.0, true); } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        public void remove() {
            try { destroy.invoke(modeled); removeModeled.invoke(null, base.getUniqueId()); } catch (ReflectiveOperationException | RuntimeException ignored) { }
            base.remove();
        }
    }
}
