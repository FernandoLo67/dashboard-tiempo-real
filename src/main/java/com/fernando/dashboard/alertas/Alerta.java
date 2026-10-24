package com.fernando.dashboard.alertas;

import java.util.Objects;

/**
 * Regla de alerta sobre una metrica. Se define con un nombre, la metrica
 * que vigila, el operador de comparacion y el umbral.
 *
 * <p>Ejemplo: alerta "memoria alta" dispara cuando memoria_uso_porcentaje > 85.
 */
public class Alerta {

    public enum Operador {
        MAYOR_QUE {
            @Override boolean evaluar(double actual, double umbral) { return actual > umbral; }
        },
        MENOR_QUE {
            @Override boolean evaluar(double actual, double umbral) { return actual < umbral; }
        },
        MAYOR_O_IGUAL {
            @Override boolean evaluar(double actual, double umbral) { return actual >= umbral; }
        },
        MENOR_O_IGUAL {
            @Override boolean evaluar(double actual, double umbral) { return actual <= umbral; }
        };

        abstract boolean evaluar(double actual, double umbral);
    }

    public enum Severidad { INFO, ADVERTENCIA, CRITICA }

    private final String nombre;
    private final String metrica;
    private final Operador operador;
    private final double umbral;
    private final Severidad severidad;

    public Alerta(String nombre, String metrica, Operador operador, double umbral, Severidad severidad) {
        this.nombre = Objects.requireNonNull(nombre);
        this.metrica = Objects.requireNonNull(metrica);
        this.operador = Objects.requireNonNull(operador);
        this.umbral = umbral;
        this.severidad = Objects.requireNonNull(severidad);
    }

    public boolean seDispara(double valorActual) {
        return operador.evaluar(valorActual, umbral);
    }

    public String getNombre() { return nombre; }
    public String getMetrica() { return metrica; }
    public Operador getOperador() { return operador; }
    public double getUmbral() { return umbral; }
    public Severidad getSeveridad() { return severidad; }

    @Override
    public String toString() {
        return "[" + severidad + "] " + nombre + ": " + metrica + " " + operador + " " + umbral;
    }
}
