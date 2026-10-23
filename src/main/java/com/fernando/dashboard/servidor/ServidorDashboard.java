package com.fernando.dashboard.servidor;

import com.fernando.dashboard.metricas.RegistroMetricas;
import com.fernando.dashboard.websocket.ServidorWebSocket;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Junta las tres piezas: un HttpServer que sirve la pagina, un servidor
 * WebSocket que empuja las metricas, y un temporizador que las recolecta.
 */
public class ServidorDashboard implements AutoCloseable {

    public static final int INTERVALO_MS = 1000;

    private final HttpServer http;
    private final ServidorWebSocket websocket;
    private final RegistroMetricas registro;
    private final RecolectorMetricas recolector;
    private final ScheduledExecutorService temporizador;
    private final LongSupplier reloj;

    public ServidorDashboard(int puertoHttp, int puertoWebSocket) throws IOException {
        this(puertoHttp, puertoWebSocket, new Random(), System::currentTimeMillis);
    }

    public ServidorDashboard(int puertoHttp, int puertoWebSocket, Random aleatorio, LongSupplier reloj)
            throws IOException {
        this.registro = new RegistroMetricas();
        this.recolector = new RecolectorMetricas(registro, aleatorio);
        this.reloj = reloj;

        this.websocket = new ServidorWebSocket(puertoWebSocket);
        this.http = HttpServer.create(new InetSocketAddress(puertoHttp), 0);
        this.http.createContext("/", this::servirPagina);
        this.http.createContext("/api/metricas", this::servirMetricas);
        this.http.setExecutor(Executors.newFixedThreadPool(4, tarea -> {
            Thread hilo = new Thread(tarea, "dashboard-http");
            hilo.setDaemon(true);
            return hilo;
        }));

        this.temporizador = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "dashboard-metricas");
            hilo.setDaemon(true);
            return hilo;
        });
    }

    public void iniciar() {
        websocket.alConectar(conexion -> conexion.enviarTexto(registro.comoJson(reloj.getAsLong())));
        websocket.iniciar();
        http.start();
        temporizador.scheduleAtFixedRate(this::tick, 0, INTERVALO_MS, TimeUnit.MILLISECONDS);
    }

    /** Un ciclo: recolectar y empujar a los conectados. */
    public void tick() {
        try {
            recolector.recolectarDeLaJvm();
            recolector.simularTrafico();
            registro.medir("clientes_conectados", websocket.clientesConectados());
            websocket.difundir(registro.comoJson(reloj.getAsLong()));
        } catch (RuntimeException e) {
            System.err.println("Error recolectando metricas: " + e.getMessage());
        }
    }

    public int getPuertoHttp() {
        return http.getAddress().getPort();
    }

    public int getPuertoWebSocket() {
        return websocket.getPuerto();
    }

    public RegistroMetricas getRegistro() {
        return registro;
    }

    public ServidorWebSocket getWebSocket() {
        return websocket;
    }

    private void servirPagina(HttpExchange intercambio) throws IOException {
        String html = cargarPlantilla().replace("{{PUERTO_WS}}", String.valueOf(getPuertoWebSocket()));
        byte[] datos = html.getBytes(StandardCharsets.UTF_8);

        intercambio.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        intercambio.sendResponseHeaders(200, datos.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(datos);
        }
    }

    private void servirMetricas(HttpExchange intercambio) throws IOException {
        byte[] datos = registro.comoJson(reloj.getAsLong()).getBytes(StandardCharsets.UTF_8);

        intercambio.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        intercambio.sendResponseHeaders(200, datos.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(datos);
        }
    }

    static String cargarPlantilla() throws IOException {
        try (InputStream entrada = ServidorDashboard.class.getResourceAsStream("/dashboard.html")) {
            if (entrada == null) {
                throw new IOException("No se encontro dashboard.html en los recursos");
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void close() {
        temporizador.shutdownNow();
        http.stop(0);
        websocket.close();
    }
}
