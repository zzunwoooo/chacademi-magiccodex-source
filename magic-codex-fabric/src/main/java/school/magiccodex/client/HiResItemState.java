package school.magiccodex.client;

import net.minecraft.util.Identifier;

/** Duck interface added to ItemRenderState by ItemRenderStateMixin. */
public interface HiResItemState {
    /** Item model drawn as a smooth flat card (see ItemIconTextures#renderWorld), or null for vanilla. */
    void magiccodex$setHiResModel(Identifier model);
}
