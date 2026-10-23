package com.fernando.dashboard.servidor;

import com.fernando.dashboard.metricas.RegistroMetricas;

import java.util.Random;

/**
 * Alimenta el registro con metricas.
 *
 * Las de la maquina (memoria, hilos, procesadores) son reales, salidas de la
 * JVM. Las de "trafico" son simuladas, porque el proyecto es una demo del
 * dashboard y no de una aplicacion concreta; estan claramente marcadas.
 */
public class RecolectorMetricas {

    private final RegistroMetricas registro;
    private final Random aleatorio;

    public RecolectorMetricas(RegistroMetricas registro, Random aleatorio) {
        this.registro = registro;
        this.aleatorio = aleatorio;
    }

    /** Metricas reales de la maquina virtual. */
    public void recolectarDeLaJvm() {
        Runtime runtime = Runtime.getRuntime();
        double usadaMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0);
        double totalMb = runtime.totalMemory() / (1024.0 * 1024.0);

        registro.medir("memoria_usada_mb", usadaMb);
        registro.medir("memoria_total_mb", totalMb);
        registro.medir("memoria_uso_porcentaje", totalMb == 0 ? 0 : usadaMb / totalMb * 100);
        registro.medir("procesadores", runtime.availableProcessors());
        registro.medir("hilos_activos", Thread.activeCount());
        registro.observar("memoria_usada_mb", usadaMb);
    }

    /** Trafico simulado, para que el dashboard tenga algo que mostrar. */
    public void simularTrafico() {
        int peticiones = 20 + aleatorio.nextInt(60);
        registro.incrementar("peticiones_totales", peticiones);

        int errores = aleatorio.nextInt(100) < 15 ? 1 + aleatorio.nextInt(3) : 0;
        if (errores > 0) {
            registro.incrementar("errores_totales", errores);
        }

        registro.medir("peticiones_por_segundo", peticiones);
        registro.medir("tasa_de_error_porcentaje",
                peticiones == 0 ? 0 : errores * 100.0 / peticiones);

        // Latencia con cola larga: la mayoria rapida, alguna lenta.
        // Es lo que hace interesante mirar p95 y p99 en vez del promedio.
        for (int i = 0; i < peticiones; i++) {
            double latencia = aleatorio.nextInt(100) < 95
                    ? 10 + aleatorio.nextDouble() * 40
                    : 200 + aleatorio.nextDouble() * 300;
            registro.observar("latencia_ms", latencia);
        }
    }
}
