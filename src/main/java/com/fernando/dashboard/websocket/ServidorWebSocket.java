package com.fernando.dashboard.websocket;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Servidor WebSocket sobre sockets crudos: hace el apreton de manos HTTP,
 * pasa a hablar marcos y difunde mensajes a todos los conectados.
 */
public class ServidorWebSocket implements AutoCloseable {

    private final ServerSocket servidor;
    private final ExecutorService pool;
    private final CopyOnWriteArrayList<Conexion> conexiones = new CopyOnWriteArrayList<>();
    private final Random aleatorio = new SecureRandom();

    private volatile boolean activo;
    private volatile BiConsumer<Conexion, String> alRecibirTexto = (conexion, texto) -> { };
    private volatile java.util.function.Consumer<Conexion> alConectar = conexion -> { };

    public ServidorWebSocket(int puerto) throws IOException {
        this.servidor = new ServerSocket(puerto);
        this.pool = Executors.newCachedThreadPool(tarea -> {
            Thread hilo = new Thread(tarea, "ws-servidor");
            hilo.setDaemon(true);
            return hilo;
        });
    }

    public int getPuerto() {
        return servidor.getLocalPort();
    }

    public void alRecibirTexto(BiConsumer<Conexion, String> manejador) {
        this.alRecibirTexto = manejador;
    }

    public void alConectar(java.util.function.Consumer<Conexion> manejador) {
        this.alConectar = manejador;
    }

    public void iniciar() {
        activo = true;
        pool.submit(this::aceptar);
    }

    private void aceptar() {
        while (activo && !servidor.isClosed()) {
            try {
                Socket socket = servidor.accept();
                pool.submit(() -> atender(socket));
            } catch (IOException e) {
                if (activo) {
                    System.err.println("Error aceptando conexion: " + e.getMessage());
                }
                return;
            }
        }
    }

    private void atender(Socket socket) {
        Conexion conexion = null;
        try {
            InputStream entrada = new BufferedInputStream(socket.getInputStream());
            OutputStream salida = socket.getOutputStream();

            String clave = leerClaveDelApreton(entrada);
            salida.write(ProtocoloWebSocket.respuestaDeApreton(clave).getBytes(StandardCharsets.UTF_8));
            salida.flush();

            conexion = new Conexion(socket, salida);
            conexiones.add(conexion);
            alConectar.accept(conexion);

            bucleDeMarcos(entrada, conexion);
        } catch (SocketException e) {
            // El cliente corto la conexion: es normal.
        } catch (IOException | ProtocoloException e) {
            System.err.println("Conexion terminada: " + e.getMessage());
        } finally {
            if (conexion != null) {
                conexiones.remove(conexion);
            }
            cerrarSilencioso(socket);
        }
    }

    private void bucleDeMarcos(InputStream entrada, Conexion conexion) throws IOException {
        while (activo && !conexion.socket.isClosed()) {
            Marco marco = ProtocoloWebSocket.decodificar(entrada);
            if (marco == null) {
                return;
            }
            switch (marco.opCode()) {
                case TEXTO:
                    alRecibirTexto.accept(conexion, marco.comoTexto());
                    break;
                case PING:
                    conexion.enviarMarco(Marco.pong(marco.datos()));
                    break;
                case CIERRE:
                    conexion.enviarMarco(Marco.cierre());
                    return;
                default:
                    // BINARIO, PONG y CONTINUACION no se usan en este dashboard.
                    break;
            }
        }
    }

    /** Lee las cabeceras HTTP del apreton y devuelve el valor de Sec-WebSocket-Key. */
    static String leerClaveDelApreton(InputStream entrada) throws IOException {
        StringBuilder cabeceras = new StringBuilder();
        int anterior = -1;
        int actual;
        // Las cabeceras terminan con una linea en blanco: \r\n\r\n
        while ((actual = entrada.read()) != -1) {
            cabeceras.append((char) actual);
            if (anterior == '\n' && actual == '\n') {
                break;
            }
            if (actual != '\r') {
                anterior = actual;
            }
        }
        for (String linea : cabeceras.toString().split("\r\n|\n")) {
            if (linea.toLowerCase().startsWith("sec-websocket-key:")) {
                return linea.substring(linea.indexOf(':') + 1).trim();
            }
        }
        throw new ProtocoloException("El apreton de manos no traia Sec-WebSocket-Key");
    }

    /** Manda un texto a todos los conectados. Devuelve a cuantos llego. */
    public int difundir(String texto) {
        int enviados = 0;
        for (Conexion conexion : conexiones) {
            if (conexion.enviarTexto(texto)) {
                enviados++;
            }
        }
        return enviados;
    }

    public int clientesConectados() {
        return conexiones.size();
    }

    public List<Conexion> conexiones() {
        return List.copyOf(conexiones);
    }

    @Override
    public void close() {
        activo = false;
        for (Conexion conexion : conexiones) {
            conexion.cerrar();
        }
        conexiones.clear();
        cerrarSilencioso(servidor);
        pool.shutdownNow();
    }

    private static void cerrarSilencioso(Closeable recurso) {
        try {
            recurso.close();
        } catch (IOException ignorado) {
            // ya estaba cerrado
        }
    }

    /** Una conexion WebSocket abierta, vista desde el servidor. */
    public class Conexion {

        private final Socket socket;
        private final OutputStream salida;
        private final Object candadoDeEscritura = new Object();

        Conexion(Socket socket, OutputStream salida) {
            this.socket = socket;
            this.salida = salida;
        }

        public boolean enviarTexto(String texto) {
            return enviarMarco(Marco.texto(texto));
        }

        public boolean enviarMarco(Marco marco) {
            // Un candado por conexion: dos hilos difundiendo a la vez no deben
            // entrelazar los bytes de dos marcos distintos.
            synchronized (candadoDeEscritura) {
                try {
                    // El servidor nunca enmascara: lo dice el RFC 6455.
                    ProtocoloWebSocket.enviar(salida, marco, false, aleatorio);
                    return true;
                } catch (IOException e) {
                    cerrar();
                    return false;
                }
            }
        }

        public boolean estaAbierta() {
            return !socket.isClosed();
        }

        public void cerrar() {
            cerrarSilencioso(socket);
        }
    }
}
