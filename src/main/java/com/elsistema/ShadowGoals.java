package com.elsistema;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

/** Inteligencia de las sombras: seguir a su Monarca y defenderle. */
public final class ShadowGoals {
    private ShadowGoals() {}

    /** Sigue al dueño cuando está lejos o cuando no hay nadie a quien atacar. */
    public static final class Follow extends Goal {
        private final Mob mob;
        private int recalc;

        public Follow(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private Player owner() {
            Player o = Shadows.ownerPlayer(this.mob);
            return o != null && o.isAlive() && o.level() == this.mob.level() ? o : null;
        }

        private boolean busy(double distSqr) {
            LivingEntity t = this.mob.getTarget();
            return t != null && t.isAlive() && distSqr < 18.0 * 18.0;
        }

        @Override
        public boolean canUse() {
            Player o = this.owner();
            if (o == null) return false;
            double d = this.mob.distanceToSqr(o);
            return !this.busy(d) && d > 4.0 * 4.0;
        }

        @Override
        public boolean canContinueToUse() {
            Player o = this.owner();
            if (o == null) return false;
            double d = this.mob.distanceToSqr(o);
            return !this.busy(d) && d > 2.5 * 2.5;
        }

        @Override
        public void start() {
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player o = this.owner();
            if (o == null) return;
            this.mob.getLookControl().setLookAt(o, 10.0F, (float) this.mob.getMaxHeadXRot());
            if (--this.recalc <= 0) {
                this.recalc = 10;
                double speed = this.mob.distanceToSqr(o) > 10.0 * 10.0 ? 1.5 : 1.15;
                this.mob.getNavigation().moveTo(o, speed);
            }
        }
    }

    /** Elige enemigo: quien le hizo daño, quien atacó al dueño, a quien atacó el dueño o el monstruo más cercano. */
    public static final class Defend extends Goal {
        private final Mob mob;
        private int cooldown;

        public Defend(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        private LivingEntity pick() {
            Player o = Shadows.ownerPlayer(this.mob);
            if (o == null || !o.isAlive() || o.level() != this.mob.level()) return null;
            LivingEntity c = this.mob.getLastHurtByMob();
            if (Shadows.validTarget(o, c) && this.mob.tickCount - this.mob.getLastHurtByMobTimestamp() < 100) return c;
            c = o.getLastHurtByMob();
            if (Shadows.validTarget(o, c) && o.tickCount - o.getLastHurtByMobTimestamp() < 160) return c;
            c = o.getLastHurtMob();
            if (Shadows.validTarget(o, c) && o.tickCount - o.getLastHurtMobTimestamp() < 160) return c;
            List<Mob> near = this.mob.level().getEntitiesOfClass(Mob.class, o.getBoundingBox().inflate(14.0, 6.0, 14.0),
                    x -> x instanceof Enemy && Shadows.validTarget(o, x) && (x.getType() != EntityType.CREEPER || x.getTarget() == o));
            Mob best = null;
            double bestD = Double.MAX_VALUE;
            for (Mob x : near) {
                double d = x.distanceToSqr(this.mob);
                if (d < bestD) {
                    bestD = d;
                    best = x;
                }
            }
            return best;
        }

        @Override
        public boolean canUse() {
            if (--this.cooldown > 0) return false;
            this.cooldown = 10;
            LivingEntity t = this.pick();
            if (t == null) return false;
            this.mob.setTarget(t);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = this.mob.getTarget();
            Player o = Shadows.ownerPlayer(this.mob);
            return t != null && o != null && Shadows.validTarget(o, t) && t.level() == o.level()
                    && t.distanceToSqr(o) < 32.0 * 32.0;
        }

        @Override
        public void stop() {
            this.mob.setTarget(null);
        }
    }
}
