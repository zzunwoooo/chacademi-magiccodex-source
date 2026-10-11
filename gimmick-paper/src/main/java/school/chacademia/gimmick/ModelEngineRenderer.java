package school.chacademia.gimmick;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;

/** ModelEngine 4 (same calls as MagicCodexBridge TamingModelFx). Loaded only when renderer: modelengine. */
final class ModelEngineRenderer implements Renderer {
    private final Logger log;
    ModelEngineRenderer(Logger log) { this.log = log; }
    public String name() { return "ModelEngine"; }
    public boolean available() { return Bukkit.getPluginManager().isPluginEnabled("ModelEngine"); }

    public Handle spawn(String modelId, Location loc, double scale) {
        ActiveModel model = ModelEngineAPI.createActiveModel(modelId);
        if (model == null) return null;
        ArmorStand anchor = loc.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setVisible(false); a.setMarker(true); a.setGravity(false); a.setInvulnerable(true); a.setSilent(true);
            GimmickWorld.prepareBase(a);
        });
        try {
            ModeledEntity modeled = ModelEngineAPI.createModeledEntity(anchor);
            modeled.setBaseEntityVisible(false);
            model.setScale(scale);
            modeled.addModel(model, true);
            return new MegHandle(anchor, modeled, model, modelId, true);
        } catch (RuntimeException | LinkageError e) {
            anchor.remove(); model.destroy();
            log.warning("ModelEngine 모델 소환 실패 " + modelId + ": " + e);
            return null;
        }
    }

    public Handle attach(String modelId, LivingEntity host, double scale) {
        ActiveModel model = ModelEngineAPI.createActiveModel(modelId);
        if (model == null) return null;
        try {
            ModeledEntity modeled = ModelEngineAPI.createModeledEntity(host);
            modeled.setBaseEntityVisible(false);
            model.setScale(scale);
            modeled.addModel(model, true);
            return new MegHandle(host, modeled, model, modelId, false);
        } catch (RuntimeException | LinkageError e) {
            model.destroy();
            log.warning("ModelEngine 모델 씌우기 실패 " + modelId + ": " + e);
            return null;
        }
    }

    public boolean hasModel(Entity host) {
        try { return ModelEngineAPI.getModeledEntity(host.getUniqueId()) != null; } catch (RuntimeException | LinkageError e) { return false; }
    }

    private static final class MegHandle implements Handle {
        final Entity base; final ModeledEntity modeled; final ActiveModel model; final String id; final boolean owned; String current = ""; final java.util.Set<String> overlays = new java.util.LinkedHashSet<>();
        MegHandle(Entity base, ModeledEntity modeled, ActiveModel model, String id, boolean owned) {
            this.base = base; this.modeled = modeled; this.model = model; this.id = id; this.owned = owned;
        }
        public Entity base() { return base; }
        public boolean play(String animation) {
            var h = model.getAnimationHandler();
            if (!current.isEmpty()) h.stopAnimation(current);
            h.playAnimation(animation, 0.1, 0.1, 1, true);
            current = animation;
            return true;
        }
        public boolean overlay(String animation) {
            model.getAnimationHandler().playAnimation(animation, 0.1, 0.1, 1, true);
            overlays.add(animation);
            return true;
        }
        public void stop() {
            var h = model.getAnimationHandler();
            if (!current.isEmpty()) h.stopAnimation(current);
            for (String o : overlays) h.stopAnimation(o);
            current = ""; overlays.clear();
        }
        public void teleport(Location loc) { base.teleport(loc); }
        public void scale(double scale) { model.setScale(scale); }
        public void remove() {
            try {
                if (owned) { modeled.destroy(); ModelEngineAPI.removeModeledEntity(base.getUniqueId()); }
                else { modeled.removeModel(id); model.destroy(); }
            } finally { if (owned) base.remove(); }
        }
    }
}
