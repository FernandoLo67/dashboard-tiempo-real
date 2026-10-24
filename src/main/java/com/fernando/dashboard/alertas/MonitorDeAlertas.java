package com.fernando.dashboard.alertas;

import com.fernando.dashboard.metricas.RegistroMetricas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Evalua un conjunto de alertas contra el estado actual de las metricas.
 *
 * <p>Se registran las alertas de antemano, y al llamar {@link #evaluar()}
 * devuelve las que estan disparadas en ese momento. Busca el valor
 * primero en medidores, luego en contadores; si la metrica no existe
 * se evalua contra 0.
 */
public class MonitorDeAlertas {

    private final RegistroMetricas registro;
    private final List<Alerta> alertas = new CopyOnWriteArrayList<>();

    public MonitorDeAlertas(RegistroMetricas registro) {
        this.registro = registro;
    }

    public void registrar(Alerta alerta) {
        alertas.add(alerta);
    }

    public List<Alerta> evaluar() {
        List<Alerta> disparadas = new ArrayList<>();
        Map<String, Double> medidores = registro.todosLosMedidores();
        Map<String, Long> contadores = registro.todosLosContadores();

        for (Alerta alerta : alertas) {
            double valor = obtenerValor(alerta.getMetrica(), medidores, contadores);
            if (alerta.seDispara(valor)) {
                disparadas.add(alerta);
            }
        }
        return Collections.unmodifiableList(disparadas);
    }

    private double obtenerValor(String metrica, Map<String, Double> medidores, Map<String, Long> contadores) {
        if (medidores.containsKey(metrica)) {
            return medidores.get(metrica);
        }
        if (contadores.containsKey(metrica)) {
            return contadores.get(metrica);
        }
        return 0.0;
    }

    public int cantidadDeAlertas() {
        return alertas.size();
    }
}
