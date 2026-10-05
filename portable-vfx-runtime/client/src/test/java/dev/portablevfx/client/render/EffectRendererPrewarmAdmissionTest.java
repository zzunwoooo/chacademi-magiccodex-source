package dev.portablevfx.client.render;

import dev.portablevfx.client.claude.ClaudeBackend;
import dev.portablevfx.client.definition.EffectLibrary;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class EffectRendererPrewarmAdmissionTest {
    @TempDir Path root;
    @Test void realCapacityPressureDefersWithoutPoisoningDefinitionsOrLeakingPreparation()throws Exception {
        Path effects=root.resolve("effects"),pack=effects.resolve("Fireball");
        Path source=Path.of(getClass().getResource("/claude/samples/Fireball/vfx.json").toURI()).getParent();
        try(var paths=Files.walk(source)){for(var file:paths.filter(Files::isRegularFile).toList()){
            Path target=pack.resolve(source.relativize(file));Files.createDirectories(target.getParent());Files.copy(file,target);
        }}
        Files.writeString(root.resolve("load-limits.properties"),"resident-rgba-mib=64\nprewarm-rgba-mib=16\n");
        var library=new EffectLibrary(()->{},effects);library.reload(null);assertTrue(library.configErrors().isEmpty());assertEquals(2,library.ids().size());
        var renderer=new EffectRenderer(null,library);Object batch=renderer.prepare(library.snapshot());
        try {
            assertTrue((int)member(batch,"deferred")>0,"Actual authored textures must encounter the16MiB ceiling");
            assertTrue(((Map<?,?>)member(batch,"errors")).isEmpty(),"Capacity is not a permanent invalid-asset error");
            assertEquals(2,library.ids().size(),"Deferred systems remain part of the admitted catalog");
            assertTrue(renderer.readyEffectIds().isEmpty(),"CPU preparations must never advertise GPU readiness");
            assertFalse(renderer.isReady());
        }finally{((AutoCloseable)batch).close();}
        assertEquals(0,((ClaudeBackend.PreparationCache)member(batch,"cache")).diagnostics().retainedBytes());
    }
    private static Object member(Object object,String name)throws Exception {
        var accessor=object.getClass().getDeclaredMethod(name);accessor.setAccessible(true);return accessor.invoke(object);
    }
}
