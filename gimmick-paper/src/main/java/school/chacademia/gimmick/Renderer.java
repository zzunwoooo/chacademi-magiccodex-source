package school.chacademia.gimmick;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Draws a bbmodel on an entity. Implementations: ItemsAdder custom entity, ModelEngine 4. */
interface Renderer {
    String name();
    boolean available();
    /** New standalone model at loc (yaw from loc). Null when the model id is unknown to the renderer. */
    Handle spawn(String modelId, Location loc, double scale);
    /** Put the model on an existing mob, hiding the mob's own look. */
    Handle attach(String modelId, LivingEntity host, double scale);
    /** True when this mob already shows a model from this renderer. */
    boolean hasModel(Entity host);

    interface Handle {
        Entity base();
        boolean play(String animation);
        void stop();
        void teleport(Location loc);
        void scale(double scale);
        /** Remove the model; standalone bases are removed too, attached hosts are kept. */
        void remove();
    }
}
