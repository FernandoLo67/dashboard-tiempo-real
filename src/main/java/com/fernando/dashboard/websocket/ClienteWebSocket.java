package com.fernando.dashboard.websocket;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Cliente WebSocket minimo. Existe sobre todo para poder probar el servidor
 * de verdad, sin depender de un navegador.
 */
public class ClienteWebSocket implements AutoCloseable {

    private final Socket socket;
    private final InputStream entrada;
    private final OutputStream salida;
    private final Random aleatorio = new SecureRandom();
    private volatile boolean activo = true;

    public ClienteWebSocket(String host, int puerto, String ruta) throws IOException {
        this.socket = new Socket(host, puerto);
        this.entrada = new BufferedInputStream(socket.getInputStream());
        this.salida = socket.getOutputStream();

        String clave = ProtocoloWebSocket.generarClaveDeCliente(aleatorio);
        salida.write(ProtocoloWebSocket.peticionDeApreton(host, puerto, ruta, clave)
                .getBytes(StandardCharsets.UTF_8));
        salida.flush();

        String respuesta = leerCabecerasDeRespuesta();
        if (!respuesta.startsWith("HTTP/1.1 101")) {
            throw new ProtocoloException("El servidor rechazo el apreton: " + respuesta.lines().findFirst().orElse(""));
        }
        String aceptacionEsperada = ProtocoloWebSocket.calcularAceptacion(clave);
        if (!respuesta.contains(aceptacionEsperada)) {
            throw new ProtocoloException("El Sec-WebSocket-Accept del servidor no coincide");
        }
    }

    private String leerCabecerasDeRespuesta() throws IOException {
        StringBuilder cabeceras = new StringBuilder();
        int anterior = -1;
        int actual;
        while ((actual = entrada.read()) != -1) {
            cabeceras.append((char) actual);
            if (anterior == '\n' && actual == '\n') {
                break;
            }
            if (actual != '\r') {
                anterior = actual;
            }
        }
        return cabeceras.toString();
    }

    /** Arranca un hilo lector que entrega cada texto recibido al consumidor. */
    public void alRecibirTexto(Consumer<String> manejador) {
        Thread lector = new Thread(() -> {
            try {
                while (activo) {
                    Marco marco = ProtocoloWebSocket.decodificar(entrada);
                    if (marco == null) {
                        return;
                    }
                    if (marco.opCode() == OpCode.TEXTO) {
                        manejador.accept(marco.comoTexto());
                    }
                }
            } catch (IOException | ProtocoloException e) {
                // conexion terminada
            }
        }, "ws-cliente-lector");
        lector.setDaemon(true);
        lector.start();
    }

    /** El cliente SIEMPRE enmascara, como exige el RFC 6455. */
    public void enviarTexto(String texto) throws IOException {
        ProtocoloWebSocket.enviar(salida, Marco.texto(texto), true, aleatorio);
    }

    public void enviarPing() throws IOException {
        ProtocoloWebSocket.enviar(salida, Marco.ping(), true, aleatorio);
    }

    @Override
    public void close() {
        activo = false;
        try {
            socket.close();
        } catch (IOException ignorado) {
            // ya estaba cerrado
        }
    }
}
