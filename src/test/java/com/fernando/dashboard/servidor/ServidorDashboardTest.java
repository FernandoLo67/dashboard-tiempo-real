package com.fernando.dashboard.servidor;

import com.fernando.dashboard.websocket.ClienteWebSocket;
import com.fernando.dashboard.websocket.ServidorWebSocket;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integracion reales: levantan el servidor WebSocket en un puerto
 * libre y conectan clientes de verdad sobre loopback.
 */
class ServidorDashboardTest {

    private static final int TIMEOUT_SEGUNDOS = 5;

    private String esperarMensaje(BlockingQueue<String> cola) throws InterruptedException {
        String mensaje = cola.poll(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        assertNotNull(mensaje, "No llego ningun mensaje en " + TIMEOUT_SEGUNDOS + "s");
        return mensaje;
    }

    @Test
    void unClienteCompletaElApretonYQuedaConectado() throws Exception {
        try (ServidorWebSocket servidor = new ServidorWebSocket(0)) {
            servidor.iniciar();

            try (ClienteWebSocket cliente = new ClienteWebSocket("127.0.0.1", servidor.getPuerto(), "/metricas")) {
                cliente.alRecibirTexto(texto -> { });
                Thread.sleep(300);

                assertEquals(1, servidor.clientesConectados());
            }
        }
    }

    @Test
    void elServidorDifundeATodosLosConectados() throws Exception {
        try (ServidorWebSocket servidor = new ServidorWebSocket(0)) {
            servidor.iniciar();
            BlockingQueue<String> recibidosPorUno = new LinkedBlockingQueue<>();
            BlockingQueue<String> recibidosPorOtro = new LinkedBlockingQueue<>();

            try (ClienteWebSocket uno = new ClienteWebSocket("127.0.0.1", servidor.getPuerto(), "/m");
                 ClienteWebSocket otro = new ClienteWebSocket("127.0.0.1", servidor.getPuerto(), "/m")) {

                uno.alRecibirTexto(recibidosPorUno::add);
                otro.alRecibirTexto(recibidosPorOtro::add);
                Thread.sleep(400);

                assertEquals(2, servidor.difundir("{\"hola\":true}"));

                assertEquals("{\"hola\":true}", esperarMensaje(recibidosPorUno));
                assertEquals("{\"hola\":true}", esperarMensaje(recibidosPorOtro));
            }
        }
    }

    @Test
    void unMensajeDelClienteLlegaAlServidorDesenmascarado() throws Exception {
        try (ServidorWebSocket servidor = new ServidorWebSocket(0)) {
            BlockingQueue<String> recibidosPorElServidor = new LinkedBlockingQueue<>();
            servidor.alRecibirTexto((conexion, texto) -> recibidosPorElServidor.add(texto));
            servidor.iniciar();

            try (ClienteWebSocket cliente = new ClienteWebSocket("127.0.0.1", servidor.getPuerto(), "/m")) {
                cliente.alRecibirTexto(texto -> { });
                Thread.sleep(300);
                cliente.enviarTexto("hola desde el cliente");

                assertEquals("hola desde el cliente", esperarMensaje(recibidosPorElServidor));
            }
        }
    }

    @Test
    void elServidorRespondeAlPingConUnPong() throws Exception {
        try (ServidorWebSocket servidor = new ServidorWebSocket(0)) {
            servidor.iniciar();

            try (ClienteWebSocket cliente = new ClienteWebSocket("127.0.0.1", servidor.getPuerto(), "/m")) {
                cliente.alRecibirTexto(texto -> { });
                Thread.sleep(300);

                // Si el servidor no respondiera, la conexion se caeria.
                assertDoesNotThrow(cliente::enviarPing);
                Thread.sleep(300);
                assertEquals(1, servidor.clientesConectados());
            }
        }
    }

    @Test
    void alConectarseUnClienteRecibeElEstadoInicial() throws Exception {
        try (ServidorWebSocket servidor = new ServidorWebSocket(0)) {
            servidor.alConectar(conexion -> conexion.enviarTexto("{\"bienvenida\":true}"));
            servidor.iniciar();
            BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();

            try (ClienteWebSocket cliente = new ClienteWebSocket("127.0.0.1", servidor.getPuerto(), "/m")) {
                cliente.alRecibirTexto(recibidos::add);
                assertEquals("{\"bienvenida\":true}", esperarMensaje(recibidos));
            }
        }
    }

    @Test
    void alDesconectarseUnClienteElServidorLoQuitaDeLaLista() throws Exception {
        try (ServidorWebSocket servidor = new ServidorWebSocket(0)) {
            servidor.iniciar();

            ClienteWebSocket cliente = new ClienteWebSocket("127.0.0.1", servidor.getPuerto(), "/m");
            cliente.alRecibirTexto(texto -> { });
            Thread.sleep(300);
            assertEquals(1, servidor.clientesConectados());

            cliente.close();
            Thread.sleep(500);

            assertEquals(0, servidor.clientesConectados());
        }
    }

    @Test
    void elDashboardSirveLaPaginaYLasMetricas() throws Exception {
        try (ServidorDashboard dashboard = new ServidorDashboard(0, 0, new Random(1), () -> 12345L)) {
            dashboard.iniciar();
            HttpClient cliente = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + dashboard.getPuertoHttp();

            HttpResponse<String> pagina = cliente.send(
                    HttpRequest.newBuilder(URI.create(base + "/")).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, pagina.statusCode());
            assertTrue(pagina.body().contains("Dashboard en Tiempo Real"));
            assertTrue(pagina.body().contains("new WebSocket"));
            assertFalse(pagina.body().contains("{{PUERTO_WS}}"),
                    "El puerto del WebSocket debe quedar sustituido en la pagina");

            HttpResponse<String> metricas = cliente.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/metricas")).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, metricas.statusCode());
            assertTrue(metricas.body().contains("\"marcaDeTiempo\":12345"));
        }
    }

    @Test
    void unTickRecolectaMetricasRealesDeLaJvm() throws IOException {
        try (ServidorDashboard dashboard = new ServidorDashboard(0, 0, new Random(7), () -> 1L)) {
            dashboard.tick();

            assertTrue(dashboard.getRegistro().medidor("memoria_usada_mb") > 0);
            assertTrue(dashboard.getRegistro().medidor("procesadores") >= 1);
            assertTrue(dashboard.getRegistro().contador("peticiones_totales") > 0);
            assertTrue(dashboard.getRegistro().serie("latencia_ms").cantidad() > 0);
        }
    }

    @Test
    void variosTicksAcumulanEnLosContadores() throws IOException {
        try (ServidorDashboard dashboard = new ServidorDashboard(0, 0, new Random(7), () -> 1L)) {
            dashboard.tick();
            long despuesDelPrimero = dashboard.getRegistro().contador("peticiones_totales");
            dashboard.tick();

            assertTrue(dashboard.getRegistro().contador("peticiones_totales") > despuesDelPrimero,
                    "Un contador acumula entre ticks");
        }
    }

    @Test
    void laPlantillaDelDashboardEstaEnLosRecursos() throws IOException {
        String html = ServidorDashboard.cargarPlantilla();

        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("{{PUERTO_WS}}"), "La plantilla lleva el marcador sin sustituir");
    }
}
