package school.magiccodex.client;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SpellYamlLoaderTest {
    @TempDir Path directory;
    private final SpellYamlLoader loader = new SpellYamlLoader();
    private static final String VALID = """
            id: magical_flame
            name: 작은 불씨
            description: |-
              손끝에서 불씨를 피웁니다.
              두 번째 줄입니다.
            category: fire
            icon: magiccodex:textures/spells/magical_flame.png
            permission: magic.learned.magical_flame
            discovery:
              description: 불이 붙은 상태로 30초 버티기
            cast:
              command: cast magicalflame
              cooldown-seconds: 10
            order: 20
            """;

    @Test void readsKoreanMultilineAndOptionalDefaultsWithoutInventingDiscovery() {
        var spell = loader.parse(VALID);
        assertEquals("작은 불씨", spell.name());
        assertEquals("손끝에서 불씨를 피웁니다.\n두 번째 줄입니다.", spell.description());
        assertEquals(CodexData.Category.FIRE, spell.category());
        assertEquals("", spell.research());
        assertEquals("cast magicalflame", spell.command());
        assertEquals(10, spell.cooldown());
        assertEquals(0, spell.manaCost());
        assertFalse(spell.discovered());
        assertFalse(spell.permissionKnown());
    }

    @Test void blankIconsAndFractionalCooldownAreExplicitlySupported() {
        var spell=loader.parse(VALID.replace("icon: magiccodex:textures/spells/magical_flame.png","icon: ''")
            .replace("cooldown-seconds: 10","cooldown-seconds: 1.5"));
        assertEquals("",spell.icon());assertEquals(1.5,spell.cooldown());
    }

    @Test void readsManaCostAndPreservesItAcrossPermissionUpdates() {
        var spell = loader.parse(VALID.replace("cooldown-seconds: 10", "cooldown-seconds: 10\n  mana-cost: 180"));
        assertEquals(180, spell.manaCost());
        assertEquals(180, spell.withPermission(true).manaCost());
        assertEquals(180, spell.withPermission(false).manaCost());
        assertEquals(180, spell.withPermission(null).manaCost());
        assertEquals(0, loader.parse(VALID.replace("cooldown-seconds: 10", "mana-cost: 0")).manaCost());
        assertEquals(1000000, loader.parse(VALID.replace("cooldown-seconds: 10", "mana-cost: 1000000")).manaCost());
    }

    @Test void retiredMetadataIsIgnoredWithoutChangingPermissionsOrCosts() {
        var plain=loader.parse(VALID);
        for(String old:List.of("rank: 고급\ncircle: 9\n","rank: 초급\ncircle: null\n","circle: 0\n")) {
            var legacy=loader.parse(VALID+old);
            assertEquals(plain,legacy);
            assertEquals(plain.withPermission(true),legacy.withPermission(true));
        }
        assertTrue(java.util.Arrays.stream(CodexData.Spell.class.getRecordComponents())
                .noneMatch(c->c.getName().equals("rank") || c.getName().equals("circle")));
    }

    @Test void finalCatalogLoads299WithUniqueOrderAndPreservedIdentity() {
        var result=loader.load(Path.of("../../spell-integration-audit/catalog-staging/hud/spells"));
        assertTrue(result.success(),result.errors().toString());
        assertEquals(299,result.spells().size());
        for(int i=0;i<299;i++) {
            var s=result.spells().get(i);
            assertEquals(i+1,s.order());
            assertFalse(s.condition().isBlank());
            assertFalse(s.permissionKnown());assertFalse(s.discovered());
        }
    }

    @Test void reloadReplacesSelectedManaCost() throws Exception {
        var file = directory.resolve("spell.yml");
        Files.writeString(file, VALID.replace("cooldown-seconds: 10", "mana-cost: 50"));
        var state = new CodexState(loader.load(directory).spells());
        state.selectSlot(0);
        assertEquals(50, state.selected().manaCost());
        Files.writeString(file, VALID.replace("cooldown-seconds: 10", "mana-cost: 90"));
        state.replaceSpells(loader.load(directory).spells());
        assertEquals(90, state.selected().manaCost());
    }

    static Stream<String> invalidFiles() {
        return Stream.of(
                VALID + "name: 중복 키\n",
                VALID.replace("category: fire", "category: all"),
                VALID.replace("category: fire", "category: typo"),
                VALID.replace("name: 작은 불씨", "name: true"),
                VALID.replace("cooldown-seconds: 10", "cooldown-seconds: -1"),
                VALID.replace("cooldown-seconds: 10", "cooldown-seconds: .nan"),
                VALID.replace("cooldown-seconds: 10", "mana-cost: -1"),
                VALID.replace("cooldown-seconds: 10", "mana-cost: 1.5"),
                VALID.replace("cooldown-seconds: 10", "mana-cost: '50'"),
                VALID.replace("cooldown-seconds: 10", "mana-cost: true"),
                VALID.replace("cooldown-seconds: 10", "mana-cost: null"),
                VALID.replace("cooldown-seconds: 10", "mana-cost: 1000001"),
                VALID.replace("permission: magic.learned.magical_flame", "permission: ''"),
                VALID.replace("textures/spells/", "textures/../"),
                VALID.replace("command: cast", "command: /cast"),
                VALID + "learned: true\n",
                VALID.replace("  command:", "\tcommand:"),
                "!!java.lang.ProcessBuilder {}"
        );
    }
    @ParameterizedTest @MethodSource("invalidFiles")
    void rejectsMalformedAndAmbiguousConfiguration(String source) {
        assertThrows(RuntimeException.class, () -> loader.parse(source));
    }

    @Test void sortsByOrderThenIdAndRemovesDeletedFiles() throws Exception {
        Files.writeString(directory.resolve("가.yml"), VALID);
        Files.writeString(directory.resolve("나.yaml"), VALID.replace("id: magical_flame", "id: second").replace("order: 20", "order: 10"));
        Files.writeString(directory.resolve("ignored.txt"), "not yaml");
        var result = loader.load(directory);
        assertTrue(result.success(), result.errors().toString());
        assertEquals(List.of("second", "magical_flame"), result.spells().stream().map(CodexData.Spell::id).toList());
        Files.delete(directory.resolve("가.yml"));
        assertEquals(1, loader.load(directory).spells().size());
    }

    @Test void duplicateIdsRejectWholeBatchAndIncludeFilename() throws Exception {
        Files.writeString(directory.resolve("a.yml"), VALID);
        Files.writeString(directory.resolve("b.yml"), VALID);
        var result = loader.load(directory);
        assertFalse(result.success());
        assertTrue(result.spells().isEmpty());
        assertTrue(result.errors().getFirst().contains("b.yml"));
        assertTrue(result.errors().getFirst().contains("중복 id"));
    }

    @Test void invalidFileDoesNotReturnAPartialCatalog() throws Exception {
        Files.writeString(directory.resolve("valid.yml"), VALID);
        Files.writeString(directory.resolve("broken.yml"), "name: [");
        var result = loader.load(directory);
        assertFalse(result.success());
        assertTrue(result.spells().isEmpty());
        assertTrue(result.errors().getFirst().contains("broken.yml"));
    }

    @Test void firstRunInstalls299AndNeverOverwritesOrRecreatesUserFiles() throws Exception {
        Path spells = directory.resolve("spells");
        loader.installDefaults(spells);
        var result = loader.load(spells);
        assertTrue(result.success(), result.errors().toString());
        assertEquals(299, result.spells().size());
        assertTrue(result.spells().stream().noneMatch(CodexData.Spell::permissionKnown));
        Files.writeString(spells.resolve("001_feather_fall.yml"), "user contents");
        Files.delete(spells.resolve("002_wind_basket.yml"));
        loader.installDefaults(spells);
        assertEquals("user contents", Files.readString(spells.resolve("001_feather_fall.yml")));
        assertFalse(Files.exists(spells.resolve("002_wind_basket.yml")));
    }

    @Test void rejectsOversizedFiles() throws Exception {
        Files.writeString(directory.resolve("oversized.yml"), "#".repeat(65537));
        assertFalse(loader.load(directory).success());
    }
    @Test void loadsThreeHundredSpellsAndStillBoundsTheCatalog() throws Exception {
        for(int i=0;i<300;i++)Files.writeString(directory.resolve("spell_"+i+".yml"),VALID.replace("id: magical_flame","id: spell_"+i));
        var loaded=loader.load(directory);
        assertTrue(loaded.success(),loaded.errors().toString());assertEquals(300,loaded.spells().size());
        for(int i=300;i<=SpellYamlLoader.MAX_SPELLS;i++)Files.writeString(directory.resolve("spell_"+i+".yml"),VALID.replace("id: magical_flame","id: spell_"+i));
        var oversized=loader.load(directory);
        assertFalse(oversized.success());assertTrue(oversized.spells().isEmpty());
    }

    @Test void unknownPermissionsAreNotTreatedAsUndiscovered() {
        var state = new CodexState(List.of(loader.parse(VALID)));
        assertEquals(1, state.visible().size());
        state.setFilter(CodexState.Filter.UNDISCOVERED);
        assertTrue(state.visible().isEmpty());
        state.setFilter(CodexState.Filter.DISCOVERED);
        assertTrue(state.visible().isEmpty());
    }

    @Test void catalogReplacementRefreshesSelectionAndClampsPageAfterDeletion() {
        var state = new CodexState(CodexData.previewSpells());
        state.selectSlot(0);
        state.replaceSpells(List.of(loader.parse(VALID.replace("name: 작은 불씨", "name: 수정한 불씨"))));
        assertEquals("수정한 불씨", state.selected().name());
        state.replaceSpells(CodexData.previewSpells());
        state.changePage(1);
        state.selectSlot(0);
        state.replaceSpells(List.of(loader.parse(VALID)));
        assertEquals(0, state.page());
        assertNull(state.selected());
    }
}
