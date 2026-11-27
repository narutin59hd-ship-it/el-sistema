package com.elsistema;

/**
 * Los sitios de la Ciudad de los Cazadores (1.9.0) a los que se puede ir al momento desde el Sistema
 * (Misiones > Ciudad > IR) estando en la ciudad. Lo usan el servidor (para llevarte) y el cliente (para la lista).
 */
public final class CityPlaces {
    private CityPlaces() {}

    /** Un sitio: nombre, quién está allí (o qué hay) y dónde apareces (mirando a la puerta). */
    public record Place(String name, String who, double x, double y, double z, float yaw) {}

    private static final int G = City.G;

    public static final Place[] LIST = {
            new Place("Plaza del Cristal", "El Cristal del Sistema y el Portal de regreso", 0.5, G, 8.5, 180.0F),
            new Place("Asociación de Cazadores", "Lucía: contratos · ascensor al mirador", 0.5, G, -17.5, 180.0F),
            new Place("Mirador de la Asociación", "Toda la ciudad a tus pies", 0.5, CityBuilder.DECK_Y, -27.5, 0.0F),
            new Place("Herrería", "Bruno: mejoras y habilidades ocultas", 28.5, G, -17.5, 180.0F),
            new Place("Alquimia", "Selene: pociones y elixires", 56.5, G, -17.5, 180.0F),
            new Place("Comedor del Cazador", "Tomás: comida y banquetes", 84.5, G, -16.5, 180.0F),
            new Place("Centro Médico", "Irene: curas, limpieza y tótems", -55.5, G, -18.5, 180.0F),
            new Place("Gremio de Cazadores", "Darío: contratos difíciles", -55.5, G, -45.5, 180.0F),
            new Place("Arena del Sistema", "Vera: combates por oleadas", 0.5, G, -42.5, 180.0F),
            new Place("Estación de Puertas", "Hugo: abre puertas de tu rango", 0.5, G, -71.5, 180.0F),
            new Place("Esencias", "Ezequiel: rarezas por esencias", 28.5, G, 16.5, 0.0F),
            new Place("Tienda 24 h", "Paco: antorchas, flechas, perlas...", 56.5, G, 16.5, 0.0F),
            new Place("Tu apartamento", "Camas, cofres, taller y encantamientos", -23.5, G, 19.5, 0.0F),
            new Place("Centro Comercial Nexo", "Atrio con fuente y escaparates", 16.5, G, 0.5, 270.0F),
            new Place("Parque Central", "Lago, cerezos y bancos", 0.5, G, 44.5, 0.0F),
            new Place("Estación Central", "El tren del río", 0.5, G, 72.5, 0.0F),
            new Place("Paseo del río", "Puentes, faroles y la orilla sur", 0.5, G, 104.5, 0.0F)};

    public static int count() {
        return LIST.length;
    }
}
