package com.fernando.dashboard.metricas;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Registro de metricas al estilo de Prometheus, reducido a lo esencial:
 *
 *   contador -> solo sube (peticiones atendidas, errores)
 *   medidor  -> sube y baja (memoria usada, conexiones activas)
 *   serie    -> historial reciente con percentiles (latencias)
 */
public class RegistroMetricas {

    private final Map<String, AtomicLong> contadores = new ConcurrentHashMap<>();
    private final Map<String, Double> medidores = new ConcurrentHashMap<>();
    private final Map<String, Serie> series = new ConcurrentHashMap<>();

    public long incrementar(String nombre) {
        return incrementar(nombre, 1);
    }

    public long incrementar(String nombre, long cuanto) {
        if (cuanto < 0) {
            throw new IllegalArgumentException("Un contador no puede bajar");
        }
        return contadores.computeIfAbsent(nombre, n -> new AtomicLong()).addAndGet(cuanto);
    }

    public long contador(String nombre) {
        AtomicLong valor = contadores.get(nombre);
        return valor == null ? 0 : valor.get();
    }

    public void medir(String nombre, double valor) {
        medidores.put(nombre, valor);
    }

    public double medidor(String nombre) {
        return medidores.getOrDefault(nombre, 0.0);
    }

    public void observar(String nombre, double valor) {
        series.computeIfAbsent(nombre, Serie::new).registrar(valor);
    }

    public Serie serie(String nombre) {
        return series.computeIfAbsent(nombre, Serie::new);
    }

    public Map<String, Long> todosLosContadores() {
        Map<String, Long> copia = new LinkedHashMap<>();
        contadores.forEach((nombre, valor) -> copia.put(nombre, valor.get()));
        return copia;
    }

    public Map<String, Double> todosLosMedidores() {
        return Map.copyOf(medidores);
    }

    public Map<String, Serie> todasLasSeries() {
        return Map.copyOf(series);
    }

    /** Vuelca todo a JSON, que es lo que se empuja por el WebSocket. */
    public String comoJson(long marcaDeTiempo) {
        StringBuilder json = new StringBuilder("{\"marcaDeTiempo\":").append(marcaDeTiempo);

        json.append(",\"contadores\":{");
        agregarEntradas(json, todosLosContadores().entrySet().stream()
                .map(e -> "\"" + escapar(e.getKey()) + "\":" + e.getValue()).collect(Collectors.toUnmodifiableList()));
        json.append("}");

        json.append(",\"medidores\":{");
        agregarEntradas(json, todosLosMedidores().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "\"" + escapar(e.getKey()) + "\":" + redondear(e.getValue())).collect(Collectors.toUnmodifiableList()));
        json.append("}");

        json.append(",\"series\":{");
        agregarEntradas(json, series.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "\"" + escapar(e.getKey()) + "\":" + serieComoJson(e.getValue())).collect(Collectors.toUnmodifiableList()));
        json.append("}}");

        return json.toString();
    }

    private String serieComoJson(Serie serie) {
        return "{\"ultimo\":" + redondear(serie.ultimo())
                + ",\"promedio\":" + redondear(serie.promedio())
                + ",\"minimo\":" + redondear(serie.minimo())
                + ",\"maximo\":" + redondear(serie.maximo())
                + ",\"p50\":" + redondear(serie.percentil(50))
                + ",\"p95\":" + redondear(serie.percentil(95))
                + ",\"p99\":" + redondear(serie.percentil(99))
                + ",\"valores\":[" + serie.valores().stream()
                        .map(this::redondear).reduce((a, b) -> a + "," + b).orElse("") + "]}";
    }

    private void agregarEntradas(StringBuilder json, java.util.List<String> entradas) {
        json.append(String.join(",", entradas));
    }

    private String redondear(double valor) {
        return String.format(java.util.Locale.ROOT, "%.2f", valor);
    }

    private String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
