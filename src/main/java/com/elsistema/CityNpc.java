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

/** Habitantes de la Ciudad de los Cazadores: tenderos (no se mueven de su puesto) y vecinos que pasean por su barrio. */
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
    // 1.9.0: la ciudad nueva (centro médico, comedor, estación de puertas, arena, tienda 24 h y más vecinos)
    public static final int HEALER = 9;
    public static final int COOK = 10;
    public static final int GATEKEEPER = 11;
    public static final int TRAINER = 12;
    public static final int SHOPKEEPER = 13;
    public static final int NOVICE = 14;
    public static final int REPORTER = 15;
    public static final int GRANDMA = 16;
    public static final int COURIER = 17;
    public static final int POLICE = 18;
    public static final int MUSICIAN = 19;
    public static final int GARDENER = 20;
    public static final int TOURIST = 21;
    public static final int ROLES = 22;

    public static final String[] NAME = {"Lucía", "Bruno", "Selene", "Ezequiel", "Darío", "Noa", "Iván", "Marta", "Ramón",
            "Irene", "Tomás", "Hugo", "Vera", "Paco", "Sora", "Elena", "Carmen", "Leo", "Agente Ruiz", "Mateo", "Inés", "Kenji"};
    public static final String[] TITLE = {"Recepcionista de la Asociación", "Herrero", "Alquimista", "Mercader de esencias",
            "Maestro del gremio", "Cazadora de rango C", "Estudiante", "Oficinista", "Vecino",
            "Doctora del Centro Médico", "Cocinero del Comedor", "Guardián de la Estación de Puertas", "Instructora de la Arena",
            "Dependiente de la Tienda 24 h", "Cazador novato", "Periodista", "Vecina", "Repartidor", "Policía", "Músico callejero",
            "Jardinera", "Turista"};
    /** Nombre de la textura (textures/entity/ciudadano/...). */
    public static final String[] SKIN = {"recepcionista", "herrero", "alquimista", "mercader", "gremio", "cazadora",
            "estudiante", "oficinista", "vecino", "doctora", "cocinero", "guardian", "instructora", "dependiente", "novato",
            "periodista", "abuela", "repartidor", "policia", "musico", "jardinera", "turista"};
    /** Los que atienden un puesto (no se mueven de él); los demás pasean por su barrio. */
    private static final boolean[] KEEPER = {true, true, true, true, true, false, false, false, false,
            true, true, true, true, true, false, false, false, false, false, false, false, false};
    /** Lo que dicen los vecinos al hablar con ellos. */
    public static final String[][] LINES = {
            {"Bienvenido a la Asociación de Cazadores.", "En el tablón tengo contratos para ti."},
            {"El acero bueno se paga con esencias.", "Tráeme esencias y tu arma no volverá a ser la misma."},
            {"Mis pociones curan casi todo.", "¿Maná? Tengo un elixir que te lo devuelve entero."},
            {"Las esencias son el alma de los monstruos.", "Compro oro y vendo maravillas."},
            {"El gremio siempre busca cazadores de verdad.", "Tengo contratos para los valientes."},
            {"Dicen que en las puertas de rango S hay jefes que nadie ha visto.",
                    "Si fallas un ¡Levántate!, prueba otra vez: tienes tres intentos.",
                    "Por la noche las runas de los colosos brillan. Es precioso... y aterrador.",
                    "Hugo, el de la Estación de Puertas, te abre una puerta de tu rango cuando quieras."},
            {"Este año me presento al examen de rango E. ¡Deséame suerte!",
                    "¿Has visto el haz de luz del Cristal? Dicen que lo puso el Sistema.",
                    "Mi hermano dice que las sombras suben de nivel contigo.",
                    "Desde el mirador de la Asociación se ve toda la ciudad. ¡Sube en el ascensor!"},
            {"Ocho horas rellenando informes de puertas... qué día.",
                    "La herrería de Bruno cobra en esencias. Ni oro ni nada.",
                    "Si te cansas de tantas partículas, mira los ajustes del Sistema.",
                    "En el Centro Comercial Nexo hay de todo. Bueno, casi."},
            {"Esta ciudad era tranquila antes de las puertas.",
                    "Un consejo: en el gremio pagan bien por los jefes.",
                    "He oído que hay armaduras que te salvan de la muerte.",
                    "¿Ya tienes tu apartamento? Está al sur de la plaza, junto al paseo."},
            {"Te curo las heridas y te quito los venenos. Para eso estoy.",
                    "Una revisión completa te deja como nuevo, maná incluido.",
                    "No vuelvas de una puerta de rango S hecho un colador, ¿eh?"},
            {"¡Bienvenido al Comedor del Cazador! Hoy el estofado está de muerte.",
                    "Un cazador con hambre es un cazador muerto.",
                    "El banquete te deja fuerte para toda una mazmorra."},
            {"Por esa puerta sólo pasan cazadores registrados.",
                    "Te abro una puerta de tu rango cuando quieras. La tarifa la pone la Asociación.",
                    "Las de rango S... mejor que no te lo cuente."},
            {"En la arena no hay botín, pero sí gloria. Y esencias.",
                    "Tres oleadas. Si caes, a la próxima lo haces mejor.",
                    "La piedra imán del foso te sube si quieres rendirte."},
            {"Abierto las 24 horas, los 365 días. Hasta con puertas abiertas.",
                    "Antorchas, flechas, perlas... Lo básico para cazar.",
                    "¿Bolsa? Son dos de oro. Es broma."},
            {"¡Acabo de despertar! Rango E, pero algún día seré de rango S.",
                    "El examen de ascenso se hace en la Asociación. Da miedo.",
                    "¿Tú también empezaste matando goblins?"},
            {"Elena, del Diario de los Cazadores. ¿Una declaración?",
                    "Dicen que hay un cazador que levanta a los muertos. ¿Le conoces?",
                    "La arena llena las portadas cada semana."},
            {"Ay, hijo, ten cuidado en esas puertas.",
                    "En mis tiempos no había monstruos, había palomas.",
                    "Los cerezos del parque están preciosos esta época."},
            {"¡Paso, paso! Llevo pociones para el Centro Médico.",
                    "Esta ciudad es enorme. Menos mal que tengo buenas piernas.",
                    "Si te pierdes, usa el Sistema: Misiones > Ciudad te lleva a cada sitio."},
            {"Todo tranquilo en la Ciudad de los Cazadores.",
                    "Romper la ciudad está prohibido. Ni lo intentes.",
                    "Si ves una puerta abierta en la calle, avisa a la Asociación."},
            {"♪ Esta canción va por los cazadores que no volvieron ♪",
                    "La acústica del paseo es la mejor de la ciudad.",
                    "Una moneda para el músico, ¿cazador?"},
            {"Riego los cerezos cada mañana. Por eso están tan bonitos.",
                    "El jardín de la Asociación es mi favorito: tiene estanque.",
                    "Las flores de la orilla del río las planté yo."},
            {"¡Qué ciudad! Torres, puentes, un haz de luz...",
                    "Vine a ver a los cazadores famosos. ¿Eres uno?",
                    "¿Me haces una foto con el Cristal del Sistema de fondo?"}};

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
        return !KEEPER[role()];
    }

    /** ¿Este oficio atiende un puesto (tienda, mostrador o ventanilla)? */
    public static boolean keeper(int role) {
        return role >= 0 && role < ROLES && KEEPER[role];
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
