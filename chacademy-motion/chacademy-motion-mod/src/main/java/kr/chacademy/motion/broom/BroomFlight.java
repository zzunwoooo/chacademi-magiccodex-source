package kr.chacademy.motion.broom;

import kr.chacademy.motion.net.BroomPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * 내 빗자루 비행 (내 PC 에서만 계산).
 *
 * <p>플레이어 자기 몸의 움직임은 원래 클라이언트가 정하므로, 바닐라 이동 계산({@code Player.travel})만
 * 빗자루 물리로 바꿔 끼운다(→ {@code PlayerTravelMixin}). 카메라가 엘리트라처럼 끊김 없이 움직이고,
 * 서버는 평소 이동 패킷만 받는다.
 *
 * <p>조작
 * <pre>
 *  W 가속(아래를 보며 W = 급강하) · S 약한 브레이크 · 마우스 방향(빗자루가 늦게 따라옴)
 *  A/D 드리프트 · A/D 두 번 배럴롤 · 스페이스 상승 · 아래를 보며 W 하강 · 달리기 키 부스트
 *  앉기 키 한 번 = 지상/공중 모두 내리기 (키 재지정 지원)
 *  낙마 후 떨어지는 빗자루 근처에서 스페이스 = 다시 잡기
 * </pre>
 */
public final class BroomFlight {
    /** 낙마 후 다시 잡을 수 있는 시간 (서버 catch-window 40틱보다 조금 짧게). */
    public static final int CATCH_TICKS = 36;

    private static BroomPhysics physics;
    private static BroomPackets.RideS2C ride;
    private static BroomRiders.Rider rider;
    private static int limitLeft;
    private static boolean prevSprint, prevShift, prevLeft, prevRight, prevJump;
    private static int leftTap, rightTap;
    private static int lastHurtTime;
    private static int crashCooldown;
    private static WindSound wind;

    // 낙마 후
    private static int catchTicks;
    private static BroomRiders.Drop drop;
    private static int awaitingCatch;
    private static boolean prevJumpCatch;

    private BroomFlight() {}

    public static boolean active() {
        return physics != null;
    }

    public static BroomPhysics physics() {
        return physics;
    }

    public static BroomPackets.RideS2C ride() {
        return ride;
    }

    public static int limitLeft() {
        return limitLeft;
    }

    public static int catchTicks() {
        return catchTicks;
    }

    // ------------------------------------------------------------------ 시작 / 끝

    /** 서버가 "타라"고 함. */
    public static void onRide(BroomPackets.RideS2C r) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean resumed = awaitingCatch > 0 || catchTicks > 0;
        if (physics != null) end(false, false); // 갈아타기
        ride = r;
        var stats = new BroomPhysics.Stats(r.maxSpeed(), r.accel(), r.turn(), r.brake(), r.stability(), r.climb(),
                r.mastery(), r.boostCooldown(), r.rollCooldown());
        Vec3 v = p.getDeltaMovement();
        physics = new BroomPhysics(stats, p.getYRot(), v.x, v.y, v.z, p.getRandom().nextLong());
        limitLeft = r.limitTicks();
        if (resumed) {
            physics.caught();
            if (drop != null) BroomRiders.drops().remove(drop);
            play(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3f, 0.8f);
        } else {
            play(SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 1.2f, 0.8f);
        }
        drop = null;
        catchTicks = 0;
        awaitingCatch = 0;
        lastHurtTime = p.hurtTime;
        prevShift = mc.options.keyShift.isDown(); // Ignore an already-held mount key until released.
        rider = BroomRiders.addSelf(p, r.broom(), resumed);
        setFlying(p, true);
        if (wind == null || wind.isStopped()) {
            wind = new WindSound(p);
            mc.getSoundManager().play(wind);
        }
    }

    /** 서버가 내리게 함 (시간 끝, 금지 구역, 명령어, 다시 마법). */
    static void serverStopped(byte reason) {
        if (physics == null) return;
        end(false, false);
        if (reason == BroomPackets.STATE_TIMEOUT) {
            Minecraft.getInstance().gui.setOverlayMessage(Component.literal("연습용 빗자루의 마력이 다했습니다"), false);
        }
    }

    /** 접속 종료 등. */
    public static void reset() {
        physics = null;
        ride = null;
        rider = null;
        catchTicks = 0;
        drop = null;
        awaitingCatch = 0;
        if (wind != null) wind.finish();
        wind = null;
    }

    private static void end(boolean fell, boolean sendDismount) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        BroomPhysics ph = physics;
        physics = null;
        rider = null;
        if (wind != null) wind.finish();
        wind = null;
        if (p == null || ph == null) return;
        // 날던 속도 그대로 몸이 튕겨 나감
        p.setDeltaMovement(ph.vx, ph.vy + (fell ? 0.25 : 0), ph.vz);
        setFlying(p, false);
        if (sendDismount) send(BroomPackets.EVT_DISMOUNT, 0);
        BroomRiders.remove(p.getUUID(), fell);
        if (fell) {
            var drops = BroomRiders.drops();
            drop = drops.isEmpty() ? null : drops.get(drops.size() - 1);
            catchTicks = CATCH_TICKS;
            prevJumpCatch = true; // 이미 누르고 있던 스페이스는 무시
        }
    }

    private static void fall() {
        send(BroomPackets.EVT_FALL, (float) (physics == null ? 0 : physics.horizontalSpeed()));
        play(SoundEvents.WOOD_BREAK, 0.7f, 1.0f);
        end(true, false);
        Minecraft.getInstance().gui.setOverlayMessage(Component.literal("균형을 잃었습니다! 스페이스로 빗자루를 잡으세요"), false);
    }

    private static void setFlying(LocalPlayer p, boolean flying) {
        // flying 을 켜 두면: 웅크리기 자세 없음, 서버의 비행 금지·낙하 피해 판정 없음.
        // 실제 움직임은 PlayerTravelMixin 이 바닐라 비행 대신 빗자루 물리로 처리한다.
        var abilities = p.getAbilities();
        if (abilities.flying != flying) {
            abilities.flying = flying;
            p.onUpdateAbilities();
        }
    }

    // ------------------------------------------------------------------ 이동 (PlayerTravelMixin 이 틱마다 부름)

    /** @return true 면 바닐라 이동 계산을 건너뜀 */
    public static boolean travel(LocalPlayer p) {
        if (physics == null) return false;
        if (p.isPassenger() || p.isSpectator() || p.isDeadOrDying()) {
            end(false, true);
            return false;
        }
        if (p.isInWater() || p.isInLava()) {
            end(false, true);
            Minecraft.getInstance().gui.setOverlayMessage(Component.literal("빗자루가 젖어서 날 수 없습니다"), false);
            return false;
        }
        var k = p.input.keyPresses;
        int rollReq = 0;
        if (leftTap > 0) leftTap--;
        if (rightTap > 0) rightTap--;
        if (crashCooldown > 0) crashCooldown--;
        boolean sprintEdge = k.sprint() && !prevSprint;
        boolean dismount = k.shift() && !prevShift;
        if (k.left() && !prevLeft) {
            if (leftTap > 0) rollReq = -1;
            leftTap = 6;
        }
        if (k.right() && !prevRight) {
            if (rightTap > 0) rollReq = 1;
            rightTap = 6;
        }
        prevSprint = k.sprint();
        prevShift = k.shift();
        prevLeft = k.left();
        prevRight = k.right();
        prevJump = k.jump();
        if (dismount) {
            end(false, true);
            return false;
        }
        if (p.hurtTime > lastHurtTime) physics.hurt();
        lastHurtTime = p.hurtTime;

        int boostBefore = physics.boostTicks, rollBefore = physics.rollTicks;
        var level = p.level();
        var input = new BroomPhysics.Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), false,
                sprintEdge, rollReq, p.getYRot(), p.getXRot(),
                level.isRainingAt(p.blockPosition()), level.isThundering(), p.getY());
        physics.step(input);

        Vec3 v = new Vec3(physics.vx, physics.vy, physics.vz);
        p.setDeltaMovement(v);
        p.move(MoverType.SELF, v);
        Vec3 a = p.getDeltaMovement();
        double impact = physics.afterMove(a.x, a.y, a.z, p.horizontalCollision, p.onGround());
        p.setDeltaMovement(physics.vx, physics.vy, physics.vz);
        p.resetFallDistance();

        if (impact > 0.08 && crashCooldown == 0) {
            crashCooldown = 10;
            play(SoundEvents.WOOD_HIT, 0.8f, (float) Math.min(1, impact * 1.5));
            send(BroomPackets.EVT_CRASH, (float) impact);
        }
        if (physics.boostTicks > boostBefore) {
            play(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4f, 0.6f);
            send(BroomPackets.EVT_BOOST, 0);
        }
        if (physics.rollTicks > rollBefore) {
            play(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.7f);
            send(physics.rollDir < 0 ? BroomPackets.EVT_ROLL_LEFT : BroomPackets.EVT_ROLL_RIGHT, 0);
            if (rider != null) rider.startRoll(physics.rollDir);
        }
        // 균형이 낮으면 화면이 흔들림
        if (physics.balance < 25) {
            float shake = (1 - physics.balance / 25f) * 0.9f;
            p.setYRot(p.getYRot() + (p.getRandom().nextFloat() - 0.5f) * shake);
            p.setXRot(Mth.clamp(p.getXRot() + (p.getRandom().nextFloat() - 0.5f) * shake, -90f, 90f));
            if (p.tickCount % 6 == 0) play(SoundEvents.BAMBOO_WOOD_HIT, 0.6f + physics.balance / 50f, 0.5f);
        }
        if (physics.fellOff()) fall();
        return true;
    }

    // ------------------------------------------------------------------ 틱 (이동 뒤)

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            if (physics != null || catchTicks > 0) reset();
            return;
        }
        if (physics != null) {
            if (limitLeft > 0) limitLeft--;
            // 땅에 닿으면 바닐라가 flying 을 끄므로, 공중에서는 다시 켜 둔다
            if (!p.onGround()) setFlying(p, true);
        }
        if (awaitingCatch > 0) awaitingCatch--;
        if (catchTicks > 0) {
            catchTicks--;
            boolean jump = p.input.keyPresses.jump();
            if (jump && !prevJumpCatch && drop != null && awaitingCatch == 0 && !p.onGround()) {
                Vec3 hip = p.position().add(0, BroomRenderer.SEAT_Y, 0);
                if (hip.distanceTo(drop.pos(1f)) < 3.0) {
                    send(BroomPackets.EVT_CATCH, 0);
                    awaitingCatch = 10;
                }
            }
            prevJumpCatch = jump;
            if (catchTicks == 0 || p.onGround()) {
                catchTicks = 0;
                drop = null;
            }
        }
    }

    /** 내 빗자루 화면 표시값 (BroomRiders.tick 이 부름). */
    static void fillVisual(BroomRiders.Rider r) {
        BroomPhysics ph = physics;
        if (ph == null) return;
        r.yaw = ph.heading;
        double h = ph.horizontalSpeed();
        r.speed = (float) h;
        float pitchTarget = (float) (-Mth.atan2(ph.vy, Math.max(h, 0.25)) * Mth.RAD_TO_DEG) * 0.6f;
        r.pitch += (Mth.clamp(pitchTarget, -28f, 28f) - r.pitch) * 0.35f;
        float bankTarget = Mth.clamp(ph.turnRate * 2.4f * Mth.clamp(ph.speedFraction() + 0.3f, 0f, 1f), -30f, 30f);
        r.bank += (bankTarget - r.bank) * 0.35f;
        r.boosting = ph.boostTicks > 0;
        r.wobbling = ph.balance < 25;
    }

    // ------------------------------------------------------------------ 도구

    static void send(byte kind, float value) {
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(BroomPackets.EventC2S.TYPE)) {
            ClientPlayNetworking.send(new BroomPackets.EventC2S(kind, value));
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** 속도에 따라 커지는 바람 소리. */
    private static final class WindSound extends AbstractTickableSoundInstance {
        private final LocalPlayer player;

        WindSound(LocalPlayer player) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0001f;
            this.relative = true;
        }

        void finish() {
            stop();
        }

        @Override
        public void tick() {
            BroomPhysics ph = physics;
            if (ph == null || player.isRemoved()) {
                stop();
                return;
            }
            float s = (float) ph.horizontalSpeed() + (float) Math.abs(ph.vy) * 0.5f;
            float target = Mth.clamp((s - 0.25f) / 1.1f, 0f, 1f) * 0.55f;
            volume += (Math.max(0.0001f, target) - volume) * 0.2f;
            pitch = 0.7f + Mth.clamp(s, 0f, 1.6f) * 0.35f;
        }
    }
}
