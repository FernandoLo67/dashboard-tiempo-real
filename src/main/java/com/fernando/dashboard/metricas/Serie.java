package com.fernando.dashboard.metricas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Ventana deslizante de las ultimas N muestras de una metrica, con estadisticas.
 *
 * Se guarda un maximo fijo de muestras porque un dashboard que corre dias
 * enteros no puede acumular memoria sin limite.
 */
public class Serie {

    public static final int MUESTRAS_POR_DEFECTO = 120;

    private final String nombre;
    private final int maxMuestras;
    private final Deque<Double> muestras = new ArrayDeque<>();

    public Serie(String nombre) {
        this(nombre, MUESTRAS_POR_DEFECTO);
    }

    public Serie(String nombre, int maxMuestras) {
        if (maxMuestras <= 0) {
            throw new IllegalArgumentException("La ventana debe tener al menos una muestra");
        }
        this.nombre = nombre;
        this.maxMuestras = maxMuestras;
    }

    public synchronized void registrar(double valor) {
        muestras.addLast(valor);
        while (muestras.size() > maxMuestras) {
            muestras.removeFirst();
        }
    }

    public String getNombre() {
        return nombre;
    }

    public synchronized int cantidad() {
        return muestras.size();
    }

    public synchronized List<Double> valores() {
        return List.copyOf(muestras);
    }

    public synchronized double ultimo() {
        return muestras.isEmpty() ? 0.0 : muestras.peekLast();
    }

    public synchronized double promedio() {
        return muestras.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public synchronized double minimo() {
        return muestras.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }

    public synchronized double maximo() {
        return muestras.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    /**
     * Percentil por el metodo del rango mas cercano: p50 es la mediana,
     * p95 el valor por debajo del cual esta el 95% de las muestras.
     */
    public synchronized double percentil(double porcentaje) {
        if (porcentaje <= 0 || porcentaje > 100) {
            throw new IllegalArgumentException("El percentil debe estar entre 0 (exclusivo) y 100");
        }
        if (muestras.isEmpty()) {
            return 0.0;
        }
        List<Double> ordenadas = new ArrayList<>(muestras);
        ordenadas.sort(null);

        int indice = (int) Math.ceil(porcentaje / 100.0 * ordenadas.size()) - 1;
        return ordenadas.get(Math.max(0, Math.min(indice, ordenadas.size() - 1)));
    }

    public synchronized void limpiar() {
        muestras.clear();
    }
}
