package com.fernando.dashboard.alertas;

import com.fernando.dashboard.metricas.RegistroMetricas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlertasTest {

    private RegistroMetricas registro;
    private MonitorDeAlertas monitor;

    @BeforeEach
    void preparar() {
        registro = new RegistroMetricas();
        monitor = new MonitorDeAlertas(registro);
    }

    @Test
    void alertaMayorQueSeDisparaAlSuperarUmbral() {
        Alerta alerta = alerta("mem alta", "memoria_uso_porcentaje",
                Alerta.Operador.MAYOR_QUE, 85, Alerta.Severidad.CRITICA);

        assertTrue(alerta.seDispara(90));
        assertFalse(alerta.seDispara(85));
        assertFalse(alerta.seDispara(50));
    }

    @Test
    void alertaMenorQueSeDisparaAlBajarDelUmbral() {
        Alerta alerta = alerta("disco bajo", "disco_libre_gb",
                Alerta.Operador.MENOR_QUE, 5, Alerta.Severidad.ADVERTENCIA);

        assertTrue(alerta.seDispara(3));
        assertFalse(alerta.seDispara(5));
        assertFalse(alerta.seDispara(10));
    }

    @Test
    void alertaMayorOIgualIncluyeElUmbral() {
        Alerta alerta = alerta("errores", "errores_totales",
                Alerta.Operador.MAYOR_O_IGUAL, 100, Alerta.Severidad.CRITICA);

        assertTrue(alerta.seDispara(100));
        assertTrue(alerta.seDispara(101));
        assertFalse(alerta.seDispara(99));
    }

    @Test
    void alertaMenorOIgualIncluyeElUmbral() {
        Alerta alerta = alerta("rps bajo", "peticiones_por_segundo",
                Alerta.Operador.MENOR_O_IGUAL, 10, Alerta.Severidad.INFO);

        assertTrue(alerta.seDispara(10));
        assertTrue(alerta.seDispara(5));
        assertFalse(alerta.seDispara(11));
    }

    @Test
    void monitorDevuelveSoloLasAlertasDisparadas() {
        registro.medir("memoria_uso_porcentaje", 92);
        registro.medir("peticiones_por_segundo", 50);

        monitor.registrar(alerta("mem alta", "memoria_uso_porcentaje",
                Alerta.Operador.MAYOR_QUE, 85, Alerta.Severidad.CRITICA));
        monitor.registrar(alerta("rps bajo", "peticiones_por_segundo",
                Alerta.Operador.MENOR_QUE, 10, Alerta.Severidad.ADVERTENCIA));

        List<Alerta> disparadas = monitor.evaluar();

        assertEquals(1, disparadas.size());
        assertEquals("mem alta", disparadas.get(0).getNombre());
    }

    @Test
    void monitorDevuelveListaVaciaSiNingunaSeDispara() {
        registro.medir("memoria_uso_porcentaje", 40);

        monitor.registrar(alerta("mem alta", "memoria_uso_porcentaje",
                Alerta.Operador.MAYOR_QUE, 85, Alerta.Severidad.CRITICA));

        assertTrue(monitor.evaluar().isEmpty());
    }

    @Test
    void monitorUsaContadoresCuandoNoHayMedidor() {
        registro.incrementar("errores_totales", 150);

        monitor.registrar(alerta("errores", "errores_totales",
                Alerta.Operador.MAYOR_O_IGUAL, 100, Alerta.Severidad.CRITICA));

        assertEquals(1, monitor.evaluar().size());
    }

    @Test
    void metricaInexistenteSeEvaluaContraCero() {
        monitor.registrar(alerta("fantasma", "no_existe",
                Alerta.Operador.MAYOR_QUE, -1, Alerta.Severidad.INFO));

        assertEquals(1, monitor.evaluar().size(), "0.0 > -1 deberia disparar");
    }

    @Test
    void laListaDeDisparadasEsInmutable() {
        registro.medir("cpu", 95);
        monitor.registrar(alerta("cpu", "cpu",
                Alerta.Operador.MAYOR_QUE, 80, Alerta.Severidad.CRITICA));

        List<Alerta> resultado = monitor.evaluar();

        assertThrows(UnsupportedOperationException.class, () -> resultado.add(null));
    }

    @Test
    void toStringIncluyeInformacionRelevante() {
        Alerta alerta = alerta("mem", "memoria", Alerta.Operador.MAYOR_QUE, 85, Alerta.Severidad.CRITICA);
        String texto = alerta.toString();

        assertTrue(texto.contains("CRITICA"));
        assertTrue(texto.contains("mem"));
        assertTrue(texto.contains("memoria"));
    }

    private Alerta alerta(String nombre, String metrica, Alerta.Operador op, double umbral, Alerta.Severidad sev) {
        return new Alerta(nombre, metrica, op, umbral, sev);
    }
}
