package io.github.willdalh.oboecraft.fabric.tsukumogami;

import net.minecraft.util.Mth;

/**
 * How one tsukumogami moves on a client: how far each of its animations has run and how strongly each shows, so the
 * model can blend from one into the next. The server never uses it. Only used on the client thread.
 *
 * <p>Each time is kept for the last tick and the one before, and a frame reads a time in between, so the animations
 * run smoothly at any frame rate.
 */
public final class TsukumogamiMotion {
    /** How long it takes to lean into watching its owner, and to straighten up again, in ticks. */
    private static final int WATCH_BLEND_TICKS = 10;
    /** How long an attack that a review or fading out interrupts takes to settle, in ticks. */
    private static final int ATTACK_SETTLE_TICKS = 6;
    /** How long it flinches when it is hurt, in ticks. */
    public static final int FLINCH_TICKS = 9;
    private static final float SECONDS_PER_TICK = 0.05F;

    private final Clock attack = new Clock();
    private final Clock watch = new Clock();
    private final Clock fade = new Clock();
    private final Clock flinch = new Clock();
    private final Blend attacking = new Blend(ATTACK_SETTLE_TICKS);
    private final Blend watching = new Blend(WATCH_BLEND_TICKS);

    /** Starts the attack's animation from its beginning, when the server says the wind-up started. */
    void startAttack() {
        attack.start();
        attacking.set(1);
    }

    /** Starts the flinch from its beginning, when it is hurt. */
    void startFlinch() {
        flinch.start();
    }

    /**
     * Moves every animation on by one tick. An attack runs to its end unless the tsukumogami stands still for a review
     * or fades out: then it stops where it is and settles, instead of finishing a hit the server never made.
     */
    void tick(boolean standsStill, boolean fading) {
        boolean attackGoesOn = attack.isRunning() && !standsStill && !fading;
        attacking.tick(attackGoesOn);
        if (attackGoesOn) {
            attack.tick();
            if (attack.ticks() >= Tsukumogami.ATTACK_ANIMATION_TICKS) {
                attack.stop();
            }
        } else if (attacking.isOff()) {
            attack.stop();
        } else {
            attack.hold();
        }

        boolean watches = standsStill && !fading;
        watching.tick(watches);
        if (watches || !watching.isOff()) {
            watch.tickOrStart();
        } else {
            watch.stop();
        }

        if (fading) {
            fade.tickOrStart();
        } else {
            fade.stop();
        }

        if (flinch.isRunning()) {
            flinch.tick();
            if (flinch.ticks() >= FLINCH_TICKS) {
                flinch.stop();
            }
        }
    }

    /** How far the attack's animation is, in seconds. */
    public float attackSeconds(float partialTick) {
        return attack.seconds(partialTick);
    }

    /** How strongly the attack shows, from 0 to 1. */
    public float attackWeight(float partialTick) {
        return attack.isRunning() ? attacking.at(partialTick) : 0;
    }

    /** How long it has watched its owner, in seconds. */
    public float watchSeconds(float partialTick) {
        return watch.seconds(partialTick);
    }

    /** How strongly watching its owner shows, from 0 to 1. */
    public float watchWeight(float partialTick) {
        return watching.at(partialTick);
    }

    /** How long it has faded out, in seconds. 0 while it doesn't. */
    public float fadeSeconds(float partialTick) {
        return fade.seconds(partialTick);
    }

    /** How long it has flinched, in seconds. 0 while it doesn't. */
    public float flinchSeconds(float partialTick) {
        return flinch.seconds(partialTick);
    }

    /** The time since an animation started, in ticks, which can stand still. */
    private static final class Clock {
        private static final int STOPPED = -1;
        private int ticks = STOPPED;
        private int ticksBefore = STOPPED;

        void start() {
            ticks = 0;
            ticksBefore = 0;
        }

        void stop() {
            ticks = STOPPED;
            ticksBefore = STOPPED;
        }

        boolean isRunning() {
            return ticks != STOPPED;
        }

        void tick() {
            ticksBefore = ticks;
            ticks++;
        }

        void tickOrStart() {
            if (isRunning()) {
                tick();
            } else {
                start();
            }
        }

        /** Keeps the time where it is for this tick. */
        void hold() {
            ticksBefore = ticks;
        }

        int ticks() {
            return ticks;
        }

        float seconds(float partialTick) {
            return isRunning() ? Mth.lerp(partialTick, ticksBefore, ticks) * SECONDS_PER_TICK : 0;
        }
    }

    /**
     * How strongly an animation shows, which rises and falls evenly over a number of ticks and is eased for a frame.
     */
    private static final class Blend {
        private final float step;
        private float weight;
        private float weightBefore;

        Blend(int ticks) {
            step = 1.0F / ticks;
        }

        void set(float weight) {
            this.weight = weight;
            weightBefore = weight;
        }

        void tick(boolean shows) {
            weightBefore = weight;
            weight = Mth.clamp(weight + (shows ? step : -step), 0, 1);
        }

        boolean isOff() {
            return weight == 0;
        }

        float at(float partialTick) {
            float linear = Mth.lerp(partialTick, weightBefore, weight);
            return linear * linear * (3 - 2 * linear);
        }
    }
}
