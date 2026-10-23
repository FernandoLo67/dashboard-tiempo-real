package com.fernando.dashboard.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

/**
 * El protocolo WebSocket (RFC 6455) implementado a mano: apreton de manos,
 * y codificacion/decodificacion de marcos.
 *
 * Estructura de un marco:
 *
 *   byte 0:  FIN(1) RSV(3) opcode(4)
 *   byte 1:  MASK(1) longitud(7)
 *   si longitud == 126 -> 2 bytes mas con la longitud real
 *   si longitud == 127 -> 8 bytes mas con la longitud real
 *   si MASK == 1       -> 4 bytes de clave de enmascarado
 *   despues, los datos (XOR con la clave si venian enmascarados)
 *
 * Regla del estandar: el cliente SIEMPRE enmascara, el servidor NUNCA.
 */
public final class ProtocoloWebSocket {

    /** El GUID fijo que define el RFC 6455, seccion 1.3. */
    public static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final int LONGITUD_16_BITS = 126;
    private static final int LONGITUD_64_BITS = 127;
    /** 10 MB: sin este limite, un marco malicioso podria pedir 2^63 bytes de memoria. */
    public static final long MAX_BYTES_POR_MARCO = 10L * 1024 * 1024;

    private ProtocoloWebSocket() {
    }

    /**
     * La respuesta al apreton de manos: SHA-1 de (clave del cliente + GUID),
     * codificado en Base64. No es seguridad, es una comprobacion de que el otro
     * extremo entiende WebSocket y no es un proxy despistado.
     */
    public static String calcularAceptacion(String claveDelCliente) {
        if (claveDelCliente == null || claveDelCliente.isBlank()) {
            throw new ProtocoloException("El cliente no envio Sec-WebSocket-Key");
        }
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] resumen = sha1.digest((claveDelCliente.trim() + GUID).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 deberia estar siempre disponible", e);
        }
    }

    public static String respuestaDeApreton(String claveDelCliente) {
        return "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + calcularAceptacion(claveDelCliente) + "\r\n\r\n";
    }

    /** Genera la clave aleatoria de 16 bytes que manda el cliente. */
    public static String generarClaveDeCliente(Random aleatorio) {
        byte[] clave = new byte[16];
        aleatorio.nextBytes(clave);
        return Base64.getEncoder().encodeToString(clave);
    }

    public static String peticionDeApreton(String host, int puerto, String ruta, String clave) {
        return "GET " + ruta + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + puerto + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + clave + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";
    }

    // ---------------- codificacion ----------------

    /** Serializa un marco. enmascarar=true solo del lado del cliente. */
    public static byte[] codificar(Marco marco, boolean enmascarar, Random aleatorio) {
        byte[] datos = marco.datos();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        salida.write((marco.fin() ? 0x80 : 0x00) | marco.opCode().getCodigo());

        int bitDeMascara = enmascarar ? 0x80 : 0x00;
        if (datos.length < LONGITUD_16_BITS) {
            salida.write(bitDeMascara | datos.length);
        } else if (datos.length <= 0xFFFF) {
            salida.write(bitDeMascara | LONGITUD_16_BITS);
            salida.write((datos.length >>> 8) & 0xFF);
            salida.write(datos.length & 0xFF);
        } else {
            salida.write(bitDeMascara | LONGITUD_64_BITS);
            for (int desplazamiento = 56; desplazamiento >= 0; desplazamiento -= 8) {
                salida.write((int) ((((long) datos.length) >>> desplazamiento) & 0xFF));
            }
        }

        if (enmascarar) {
            byte[] mascara = new byte[4];
            aleatorio.nextBytes(mascara);
            salida.write(mascara, 0, 4);
            for (int i = 0; i < datos.length; i++) {
                salida.write(datos[i] ^ mascara[i % 4]);
            }
        } else {
            salida.write(datos, 0, datos.length);
        }
        return salida.toByteArray();
    }

    // ---------------- decodificacion ----------------

    /** Lee un marco completo del flujo. Devuelve null si el otro extremo cerro. */
    public static Marco decodificar(InputStream entrada) throws IOException {
        int primerByte = entrada.read();
        if (primerByte == -1) {
            return null;
        }
        boolean fin = (primerByte & 0x80) != 0;
        if ((primerByte & 0x70) != 0) {
            throw new ProtocoloException("Los bits RSV deben venir en cero");
        }
        OpCode opCode = OpCode.desde(primerByte & 0x0F);

        int segundoByte = leerByte(entrada);
        boolean enmascarado = (segundoByte & 0x80) != 0;
        long longitud = segundoByte & 0x7F;

        if (longitud == LONGITUD_16_BITS) {
            longitud = (leerByte(entrada) << 8) | leerByte(entrada);
        } else if (longitud == LONGITUD_64_BITS) {
            longitud = 0;
            for (int i = 0; i < 8; i++) {
                longitud = (longitud << 8) | leerByte(entrada);
            }
            if (longitud < 0) {
                throw new ProtocoloException("Longitud de marco invalida");
            }
        }

        if (longitud > MAX_BYTES_POR_MARCO) {
            throw new ProtocoloException("Marco demasiado grande: " + longitud + " bytes");
        }
        if (opCode.esDeControl() && longitud > 125) {
            throw new ProtocoloException("Un marco de control no puede pasar de 125 bytes");
        }

        byte[] mascara = new byte[4];
        if (enmascarado) {
            leerExacto(entrada, mascara);
        }

        byte[] datos = new byte[(int) longitud];
        leerExacto(entrada, datos);

        if (enmascarado) {
            for (int i = 0; i < datos.length; i++) {
                datos[i] = (byte) (datos[i] ^ mascara[i % 4]);
            }
        }
        return new Marco(fin, opCode, datos);
    }

    public static void enviar(OutputStream salida, Marco marco, boolean enmascarar, Random aleatorio)
            throws IOException {
        salida.write(codificar(marco, enmascarar, aleatorio));
        salida.flush();
    }

    private static int leerByte(InputStream entrada) throws IOException {
        int valor = entrada.read();
        if (valor == -1) {
            throw new java.io.EOFException("El flujo termino en medio de un marco");
        }
        return valor;
    }

    private static void leerExacto(InputStream entrada, byte[] destino) throws IOException {
        int leidos = 0;
        while (leidos < destino.length) {
            int n = entrada.read(destino, leidos, destino.length - leidos);
            if (n == -1) {
                throw new java.io.EOFException("El flujo termino antes de completar el marco");
            }
            leidos += n;
        }
    }
}
