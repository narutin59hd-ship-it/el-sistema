package com.elsistema;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Habitantes de la Ciudad de los Cazadores: tenderos (no se mueven) y vecinos que pasean por la plaza. */
public class CityNpc extends PathfinderMob {
    public static final int RECEPTION = 0;
    public static final int SMITH = 1;
    public static final int ALCHEMIST = 2;
    public static final int MERCHANT = 3;
    public static final int GUILD = 4;
    public static final int HUNTER = 5;
    public static final int STUDENT = 6;
    public static final int CLERK = 7;
    public static final int NEIGHBOR = 8;
    public static final int ROLES = 9;

    public static final String[] NAME = {"Lucía", "Bruno", "Selene", "Ezequiel", "Darío", "Noa", "Iván", "Marta", "Ramón"};
    public static final String[] TITLE = {"Recepcionista de la Asociación", "Herrero", "Alquimista", "Mercader de esencias",
            "Maestro del gremio", "Cazadora de rango C", "Estudiante", "Oficinista", "Vecino"};
    /** Nombre de la textura (textures/entity/ciudadano/...). */
    public static final String[] SKIN = {"recepcionista", "herrero", "alquimista", "mercader", "gremio", "cazadora",
            "estudiante", "oficinista", "vecino"};
    /** Lo que dicen los vecinos al hablar con ellos. */
    public static final String[][] LINES = {
            {"Bienvenido a la Asociación de Cazadores.", "En el tablón tengo contratos para ti."},
            {"El acero bueno se paga con esencias.", "Tráeme esencias y tu arma no volverá a ser la misma."},
            {"Mis pociones curan casi todo.", "¿Maná? Tengo un elixir que te lo devuelve entero."},
            {"Las esencias son el alma de los monstruos.", "Compro oro y vendo maravillas."},
            {"El gremio siempre busca cazadores de verdad.", "Tengo contratos para los valientes."},
            {"Dicen que en las puertas de rango S hay jefes que nadie ha visto.",
                    "Si fallas un ¡Levántate!, prueba otra vez: tienes tres intentos.",
                    "Por la noche las runas de los colosos brillan. Es precioso... y aterrador."},
            {"Este año me presento al examen de rango E. ¡Deséame suerte!",
                    "¿Has visto el cristal de la plaza? Dicen que lo puso el Sistema.",
                    "Mi hermano dice que las sombras suben de nivel contigo."},
            {"Ocho horas rellenando informes de puertas... qué día.",
                    "La herrería de Bruno cobra en esencias. Ni oro ni nada.",
                    "Si te cansas de tantas partículas, mira los ajustes del Sistema."},
            {"Esta ciudad era tranquila antes de las puertas.",
                    "Un consejo: en el gremio pagan bien por los jefes.",
                    "He oído que hay armaduras que te salvan de la muerte."}};

    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(CityNpc.class, EntityDataSerializers.INT);
    private BlockPos home = BlockPos.ZERO;

    public CityNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, 0);
    }

    public int role() {
        int r = this.entityData.get(ROLE);
        return r >= 0 && r < ROLES ? r : 0;
    }

    public void setRole(int r) {
        this.entityData.set(ROLE, Math.max(0, Math.min(ROLES - 1, r)));
        this.setCustomName(Component.literal(NAME[role()]).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" · " + TITLE[role()]).withStyle(ChatFormatting.GRAY)));
        this.setCustomNameVisible(true);
    }

    /** Los vecinos pasean; los tenderos y las recepcionistas se quedan en su puesto. */
    public boolean wanders() {
        return role() >= HUNTER;
    }

    public void setHome(BlockPos pos) {
        this.home = pos.immutable();
        this.restrictTo(this.home, wanders() ? 14 : 2);
    }

    public BlockPos home() {
        return this.home;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.6));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return CityNpc.this.wanders() && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
            this.getLookControl().setLookAt(sp, 30.0F, 30.0F);
            City.interact(sp, this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return wanders();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Rol", role());
        tag.putInt("CasaX", home.getX());
        tag.putInt("CasaY", home.getY());
        tag.putInt("CasaZ", home.getZ());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRole(tag.getInt("Rol"));
        setHome(new BlockPos(tag.getInt("CasaX"), tag.getInt("CasaY"), tag.getInt("CasaZ")));
    }
}
