package kr.chacademy.cutscene.client;

import com.mojang.blaze3d.platform.NativeImage;
import kr.chacademy.cutscene.data.Cutscene;
import kr.chacademy.cutscene.data.CutsceneLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 컷신 하나에 필요한 이미지들을 읽어 텍스처로 올리고, 끝나면 내린다. */
public final class CutsceneTextures implements AutoCloseable {
    /** 이보다 큰 이미지는 줄여서 올린다 (메모리와 전환 계산량 때문). */
    private static final int MAX_SIDE = 1600;

    public record Tex(ResourceLocation id, int width, int height, NativeImage image) {
    }

    private final TextureManager manager = Minecraft.getInstance().getTextureManager();
    private final Map<String, Tex> byName = new HashMap<>();
    private final List<ResourceLocation> registered = new ArrayList<>();
    private final String prefix;
    private int counter = 0;

    public CutsceneTextures(Cutscene cutscene) throws IOException {
        this("cutscene/" + cutscene.id(), CutsceneLoader.folder(cutscene.id()),
                cutscene.scenes().stream().flatMap(s -> s.images().stream()).distinct().toList());
    }

    /** 폴더 안의 PNG 들을 읽어 올린다. 대화 일러스트에도 쓴다. 없는 파일은 missingOk 이면 건너뜀. */
    public CutsceneTextures(String key, Path folder, java.util.Collection<String> names) throws IOException {
        this(key, folder, names, false);
    }

    public CutsceneTextures(String key, Path folder, java.util.Collection<String> names, boolean missingOk) throws IOException {
        this.prefix = "dyn/" + key + "/" + Long.toHexString(System.nanoTime()) + "/";
        try {
            for (String name : names) {
                if (byName.containsKey(name)) continue;
                Path file = folder.resolve(name);
                if (missingOk && !Files.isRegularFile(file)) continue;
                byName.put(name, load(file, name));
            }
        } catch (IOException | RuntimeException e) {
            close();
            throw e instanceof IOException io ? io : new IOException(e.getMessage(), e);
        }
    }

    private Tex load(Path file, String name) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("이미지 없음: " + name);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IOException("PNG만 쓸 수 있어요 (편집기에서 내보내면 자동 변환): " + name);
        }
        NativeImage img;
        try (InputStream in = Files.newInputStream(file)) {
            img = NativeImage.read(in);
        }
        int w = img.getWidth(), h = img.getHeight();
        int longSide = Math.max(w, h);
        if (longSide > MAX_SIDE) {
            double s = (double) MAX_SIDE / longSide;
            int nw = Math.max(1, (int) Math.round(w * s)), nh = Math.max(1, (int) Math.round(h * s));
            NativeImage scaled = new NativeImage(nw, nh, false);
            img.resizeSubRectTo(0, 0, w, h, scaled);
            img.close();
            img = scaled;
            w = nw;
            h = nh;
        }
        return register(img);
    }

    /** NativeImage 를 새 텍스처로 올린다. image 는 텍스처가 소유한다. */
    public Tex register(NativeImage img) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("chaca_story", prefix + (counter++));
        DynamicTexture tex = new DynamicTexture(img);
        tex.setFilter(true, false);
        manager.register(id, tex);
        registered.add(id);
        return new Tex(id, img.getWidth(), img.getHeight(), img);
    }

    public Tex get(String name) {
        return byName.get(name);
    }

    /** 텍스처 내용을 바꾼 뒤 GPU에 다시 올린다. */
    public void upload(Tex tex) {
        if (manager.getTexture(tex.id()) instanceof DynamicTexture dyn) dyn.upload();
    }

    @Override
    public void close() {
        for (ResourceLocation id : registered) manager.release(id);
        registered.clear();
        byName.clear();
    }
}
