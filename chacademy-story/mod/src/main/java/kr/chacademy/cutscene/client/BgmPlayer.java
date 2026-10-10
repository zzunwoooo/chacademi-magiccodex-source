package kr.chacademy.cutscene.client;

import kr.chacademy.cutscene.ChacademyCutsceneClient;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 컷신 배경음. config 폴더의 ogg 를 직접 읽어 OpenAL 로 재생한다
 * (리소스팩 없이 컷신 폴더에 ogg 만 넣으면 되도록).
 *
 * <p>볼륨 = 컷신에서 정한 bgm_volume × 페이드 × 마인크래프트 [주 음량] 설정 ([음악] 설정과는 무관).
 * 모든 호출은 클라이언트(렌더) 스레드에서 한다.
 */
public final class BgmPlayer {
    private static int source = 0;
    private static int buffer = 0;
    private static double baseVolume = 0.6;
    /** 컷신이 정해 주는 페이드 값 (0~1). */
    private static double level = 0;
    /** 컷신이 끝난 뒤 스스로 꺼지는 중이면 초당 줄어드는 양. */
    private static double releasePerSecond = 0;

    private BgmPlayer() {}

    public static boolean playing() {
        return source != 0;
    }

    /** 처음부터 재생. 소리는 0에서 시작하고 setLevel 로 올린다. */
    public static void start(Path file, double volume, double startSeconds) throws IOException {
        stopNow();
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
        ShortBuffer pcm = null;
        try {
            data.put(bytes).flip();
            int channels, rate;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer ch = stack.mallocInt(1);
                IntBuffer sr = stack.mallocInt(1);
                pcm = STBVorbis.stb_vorbis_decode_memory(data, ch, sr);
                if (pcm == null) throw new IOException("ogg 를 읽지 못했어요: " + file.getFileName());
                channels = ch.get(0);
                rate = sr.get(0);
            }
            int format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            if (channels > 2) throw new IOException("모노 또는 스테레오 ogg 만 쓸 수 있어요");
            buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, format, pcm, rate);
            source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            // 반복 재생 안 함 (곡이 컷신보다 짧으면 끝난 뒤 조용해짐)
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
            AL10.alSourcef(source, AL10.AL_GAIN, 0);
            if (startSeconds > 0) AL10.alSourcef(source, AL11.AL_SEC_OFFSET, (float) startSeconds);
            AL10.alSourcePlay(source);
            int err = AL10.alGetError();
            if (err != AL10.AL_NO_ERROR) {
                stopNow();
                throw new IOException("OpenAL 오류 " + err);
            }
        } finally {
            MemoryUtil.memFree(data);
            if (pcm != null) LibCStdlib.free(pcm);
        }
        baseVolume = volume;
        level = 0;
        releasePerSecond = 0;
        // 바닐라 배경음악과 겹치지 않게
        Minecraft.getInstance().getMusicManager().stopPlaying();
    }

    /** 컷신이 매 프레임 알려 주는 페이드 값. */
    public static void setLevel(double value) {
        if (source == 0 || releasePerSecond > 0) return;
        level = Math.max(0, Math.min(1, value));
        applyGain();
    }

    /** 컷신이 끝나거나 건너뛰었을 때. 지금 크기에서 seconds 동안 줄어들다 꺼진다. */
    public static void release(double seconds) {
        if (source == 0) return;
        if (level <= 0.001 || seconds <= 0) {
            stopNow();
            return;
        }
        releasePerSecond = level / seconds;
    }

    /** 매 틱 (1/20초) 호출. 끝난 뒤 페이드와 설정 볼륨 반영. */
    public static void tick() {
        if (source == 0) return;
        if (releasePerSecond > 0) {
            level -= releasePerSecond / 20.0;
            if (level <= 0) {
                stopNow();
                return;
            }
        }
        else {
            // 컷신 도중 바닐라 배경음악이 다시 시작되지 않게
            Minecraft.getInstance().getMusicManager().stopPlaying();
        }
        applyGain();
    }

    private static void applyGain() {
        var options = Minecraft.getInstance().options;
        // [음악]은 바닐라 배경음악 때문에 꺼 두는 사람이 많아서 [주 음량]만 따른다
        double settings = options.getSoundSourceVolume(SoundSource.MASTER);
        AL10.alSourcef(source, AL10.AL_GAIN, (float) (baseVolume * level * settings));
    }

    public static void stopNow() {
        try {
            if (source != 0) {
                AL10.alSourceStop(source);
                AL10.alDeleteSources(source);
            }
            if (buffer != 0) AL10.alDeleteBuffers(buffer);
        } catch (Throwable t) {
            ChacademyCutsceneClient.LOGGER.warn("배경음 정리 실패", t);
        }
        source = 0;
        buffer = 0;
        level = 0;
        releasePerSecond = 0;
    }
}
