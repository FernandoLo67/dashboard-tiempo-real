package com.fernando.dashboard.metricas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricasTest {

    private static final double DELTA = 0.0001;

    private RegistroMetricas registro;

    @BeforeEach
    void prepararRegistro() {
        registro = new RegistroMetricas();
    }

    // ---------------- contadores y medidores ----------------

    @Test
    void unContadorEmpiezaEnCeroYSoloSube() {
        assertEquals(0, registro.contador("peticiones"));

        registro.incrementar("peticiones");
        registro.incrementar("peticiones", 5);

        assertEquals(6, registro.contador("peticiones"));
    }

    @Test
    void unContadorNoAdmiteIncrementosNegativos() {
        assertThrows(IllegalArgumentException.class, () -> registro.incrementar("x", -1));
    }

    @Test
    void unMedidorSubeYBaja() {
        registro.medir("memoria", 100.5);
        assertEquals(100.5, registro.medidor("memoria"), DELTA);

        registro.medir("memoria", 42.0);
        assertEquals(42.0, registro.medidor("memoria"), DELTA);
    }

    @Test
    void unMedidorNoRegistradoValeCero() {
        assertEquals(0.0, registro.medidor("no-existe"), DELTA);
    }

    // ---------------- series y percentiles ----------------

    @Test
    void unaSerieVaciaDevuelveCeros() {
        Serie serie = new Serie("vacia");

        assertEquals(0, serie.cantidad());
        assertEquals(0.0, serie.promedio(), DELTA);
        assertEquals(0.0, serie.percentil(95), DELTA);
        assertEquals(0.0, serie.ultimo(), DELTA);
    }

    @Test
    void laSerieCalculaSusEstadisticasBasicas() {
        Serie serie = new Serie("latencia");
        for (double valor : new double[]{10, 20, 30, 40, 50}) {
            serie.registrar(valor);
        }

        assertEquals(5, serie.cantidad());
        assertEquals(30.0, serie.promedio(), DELTA);
        assertEquals(10.0, serie.minimo(), DELTA);
        assertEquals(50.0, serie.maximo(), DELTA);
        assertEquals(50.0, serie.ultimo(), DELTA);
    }

    @Test
    void losPercentilesSeCalculanPorRangoMasCercano() {
        Serie serie = new Serie("latencia");
        for (int i = 1; i <= 100; i++) {
            serie.registrar(i);
        }

        assertEquals(50.0, serie.percentil(50), DELTA);
        assertEquals(95.0, serie.percentil(95), DELTA);
        assertEquals(99.0, serie.percentil(99), DELTA);
        assertEquals(100.0, serie.percentil(100), DELTA);
    }

    @Test
    void elPercentilNoSeDejaEnganarPorElOrdenDeLlegada() {
        Serie enOrden = new Serie("a");
        Serie desordenada = new Serie("b");
        for (int i = 1; i <= 10; i++) {
            enOrden.registrar(i);
        }
        for (int i : new int[]{7, 2, 9, 4, 1, 10, 3, 8, 5, 6}) {
            desordenada.registrar(i);
        }

        assertEquals(enOrden.percentil(50), desordenada.percentil(50), DELTA);
        assertEquals(enOrden.percentil(95), desordenada.percentil(95), DELTA);
    }

    @Test
    void elP95CapturaLaColaLarga() {
        Serie serie = new Serie("latencia");
        for (int i = 0; i < 95; i++) {
            serie.registrar(20);
        }
        for (int i = 0; i < 5; i++) {
            serie.registrar(500);
        }

        // El promedio esconde la cola; el p99 la muestra.
        assertEquals(44.0, serie.promedio(), DELTA);
        assertEquals(20.0, serie.percentil(50), DELTA);
        assertEquals(500.0, serie.percentil(99), DELTA);
    }

    @Test
    void unPercentilFueraDeRangoEsRechazado() {
        Serie serie = new Serie("x");
        assertThrows(IllegalArgumentException.class, () -> serie.percentil(0));
        assertThrows(IllegalArgumentException.class, () -> serie.percentil(101));
    }

    @Test
    void laVentanaDeslizanteDescartaLoMasViejo() {
        Serie serie = new Serie("corta", 3);
        for (double valor : new double[]{1, 2, 3, 4, 5}) {
            serie.registrar(valor);
        }

        assertEquals(3, serie.cantidad());
        assertIterableEquals(java.util.List.of(3.0, 4.0, 5.0), serie.valores());
    }

    @Test
    void unaVentanaSinTamanoEsRechazada() {
        assertThrows(IllegalArgumentException.class, () -> new Serie("x", 0));
    }

    // ---------------- serializacion ----------------

    @Test
    void elJsonIncluyeContadoresMedidoresYSeries() {
        registro.incrementar("peticiones", 42);
        registro.medir("memoria_mb", 128.5);
        registro.observar("latencia_ms", 15.5);

        String json = registro.comoJson(1000L);

        assertTrue(json.contains("\"marcaDeTiempo\":1000"));
        assertTrue(json.contains("\"peticiones\":42"));
        assertTrue(json.contains("\"memoria_mb\":128.50"));
        assertTrue(json.contains("\"latencia_ms\":{"));
        assertTrue(json.contains("\"p95\":"));
        assertTrue(json.contains("\"valores\":[15.50]"));
    }

    @Test
    void unRegistroVacioProduceJsonValido() {
        assertEquals("{\"marcaDeTiempo\":0,\"contadores\":{},\"medidores\":{},\"series\":{}}",
                registro.comoJson(0));
    }

    @Test
    void elJsonUsaSiemprePuntoDecimalSinImportarElIdioma() {
        registro.medir("valor", 3.5);
        assertTrue(registro.comoJson(0).contains("3.50"),
                "Con locale espanol un formato descuidado daria 3,50 y romperia el JSON");
    }

    @Test
    void lasSeriesSeCreanSolasAlObservar() {
        registro.observar("nueva", 1.0);
        assertEquals(1, registro.serie("nueva").cantidad());
        assertTrue(registro.todasLasSeries().containsKey("nueva"));
    }
}
