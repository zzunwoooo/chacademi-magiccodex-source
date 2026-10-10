package kr.chacademy.storyplugin;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;
import java.util.function.BooleanSupplier;
/** Persist claims before effects: crash recovery is at-most-once, not transactional delivery. */
final class StorySafety {
    static boolean once(Set<String> fired, String event, BooleanSupplier persist, Runnable effect) {
        if (!fired.add(event)) return false;
        if (!persist.getAsBoolean()) { fired.remove(event); return false; }
        effect.run(); return true;
    }
    static boolean declaredEvent(String event, Set<String> commands, Set<String> affinity) {
        return event != null && event.matches("[a-z0-9_-]{1,64}")
                && (commands.contains(event) || affinity.contains(event));
    }
    static void atomicWrite(Path target, String contents) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(target.toAbsolutePath().getParent(), "story-progress-", ".tmp");
        try {
            try (FileChannel channel=FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer bytes=StandardCharsets.UTF_8.encode(contents);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
