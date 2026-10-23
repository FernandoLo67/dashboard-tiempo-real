package com.fernando.dashboard.websocket;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ProtocoloWebSocketTest {

    private final Random aleatorio = new Random(42);

    // ---------------- apreton de manos ----------------

    @Test
    void elApretonCoincideConElEjemploDelRfc6455() {
        // Vector de prueba oficial del RFC 6455, seccion 1.3.
        // Si esta linea pasa, el apreton es correcto segun el estandar.
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                ProtocoloWebSocket.calcularAceptacion("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    @Test
    void elApretonIgnoraEspaciosAlrededorDeLaClave() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                ProtocoloWebSocket.calcularAceptacion("  dGhlIHNhbXBsZSBub25jZQ==  "));
    }

    @Test
    void sinClaveElApretonFalla() {
        assertThrows(ProtocoloException.class, () -> ProtocoloWebSocket.calcularAceptacion(null));
        assertThrows(ProtocoloException.class, () -> ProtocoloWebSocket.calcularAceptacion("   "));
    }

    @Test
    void laRespuestaDelApretonTieneLasCabecerasObligatorias() {
        String respuesta = ProtocoloWebSocket.respuestaDeApreton("dGhlIHNhbXBsZSBub25jZQ==");

        assertTrue(respuesta.startsWith("HTTP/1.1 101 Switching Protocols\r\n"));
        assertTrue(respuesta.contains("Upgrade: websocket"));
        assertTrue(respuesta.contains("Connection: Upgrade"));
        assertTrue(respuesta.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="));
        assertTrue(respuesta.endsWith("\r\n\r\n"), "Las cabeceras terminan con una linea en blanco");
    }

    @Test
    void laClaveDelClienteTieneDieciseisBytes() {
        String clave = ProtocoloWebSocket.generarClaveDeCliente(aleatorio);
        assertEquals(16, java.util.Base64.getDecoder().decode(clave).length);
    }

    @Test
    void seExtraeLaClaveDeLasCabecerasDelApreton() throws IOException {
        String peticion = "GET /metricas HTTP/1.1\r\n"
                + "Host: localhost:8081\r\n"
                + "Upgrade: websocket\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";

        assertEquals("dGhlIHNhbXBsZSBub25jZQ==", ServidorWebSocket.leerClaveDelApreton(
                new ByteArrayInputStream(peticion.getBytes(StandardCharsets.UTF_8))));
    }

    // ---------------- marcos ----------------

    private Marco idaYVuelta(Marco original, boolean enmascarar) throws IOException {
        byte[] bytes = ProtocoloWebSocket.codificar(original, enmascarar, aleatorio);
        return ProtocoloWebSocket.decodificar(new ByteArrayInputStream(bytes));
    }

    @Test
    void unMarcoDeTextoSobreviveLaIdaYVuelta() throws IOException {
        Marco recuperado = idaYVuelta(Marco.texto("hola mundo"), false);

        assertEquals(OpCode.TEXTO, recuperado.opCode());
        assertTrue(recuperado.fin());
        assertEquals("hola mundo", recuperado.comoTexto());
    }

    @Test
    void unMarcoEnmascaradoSeDesenmascaraBien() throws IOException {
        assertEquals("mensaje del cliente",
                idaYVuelta(Marco.texto("mensaje del cliente"), true).comoTexto());
    }

    @Test
    void elEnmascaradoCambiaLosBytesEnElCable() {
        Marco marco = Marco.texto("AAAAAAAAAA");
        byte[] sinMascara = ProtocoloWebSocket.codificar(marco, false, aleatorio);
        byte[] conMascara = ProtocoloWebSocket.codificar(marco, true, aleatorio);

        assertTrue(conMascara.length > sinMascara.length,
                "El enmascarado agrega 4 bytes de clave");
        assertEquals(sinMascara.length + 4, conMascara.length);
        assertNotEquals((byte) 'A', conMascara[conMascara.length - 1],
                "Los datos enmascarados no deben verse en claro");
    }

    @Test
    void elBitDeMascaraSeMarcaEnLaCabecera() {
        byte[] conMascara = ProtocoloWebSocket.codificar(Marco.texto("x"), true, aleatorio);
        byte[] sinMascara = ProtocoloWebSocket.codificar(Marco.texto("x"), false, aleatorio);

        assertNotEquals(0, conMascara[1] & 0x80);
        assertEquals(0, sinMascara[1] & 0x80);
    }

    @Test
    void losAcentosSobrevivenPorqueSeUsaUtf8() throws IOException {
        assertEquals("Mañana será un día soleado",
                idaYVuelta(Marco.texto("Mañana será un día soleado"), true).comoTexto());
    }

    @Test
    void lasTresFormasDeCodificarLaLongitudFuncionan() throws IOException {
        // 125 bytes: cabe en los 7 bits
        String corto = "a".repeat(125);
        assertEquals(corto, idaYVuelta(Marco.texto(corto), false).comoTexto());

        // 126 bytes: hace falta el campo de 16 bits
        String medio = "b".repeat(126);
        assertEquals(medio, idaYVuelta(Marco.texto(medio), false).comoTexto());

        // 70000 bytes: hace falta el campo de 64 bits
        String largo = "c".repeat(70000);
        assertEquals(largo, idaYVuelta(Marco.texto(largo), false).comoTexto());
    }

    @Test
    void laLongitudUsaElCampoCortoSoloHastaCientoVeinticinco() {
        assertEquals(125, ProtocoloWebSocket.codificar(
                Marco.texto("a".repeat(125)), false, aleatorio)[1] & 0x7F);
        assertEquals(126, ProtocoloWebSocket.codificar(
                Marco.texto("a".repeat(126)), false, aleatorio)[1] & 0x7F);
        assertEquals(127, ProtocoloWebSocket.codificar(
                Marco.texto("a".repeat(70000)), false, aleatorio)[1] & 0x7F);
    }

    @Test
    void enElLimiteDeSesentaYCincoMilQuinientosTreintaYCincoSiguenSiendoDieciseisBits() {
        assertEquals(126, ProtocoloWebSocket.codificar(
                Marco.texto("a".repeat(65535)), false, aleatorio)[1] & 0x7F);
        assertEquals(127, ProtocoloWebSocket.codificar(
                Marco.texto("a".repeat(65536)), false, aleatorio)[1] & 0x7F);
    }

    @Test
    void losMarcosDeControlSobrevivenLaIdaYVuelta() throws IOException {
        assertEquals(OpCode.PING, idaYVuelta(Marco.ping(), true).opCode());
        assertEquals(OpCode.PONG, idaYVuelta(Marco.pong(new byte[]{1, 2, 3}), true).opCode());
        assertEquals(OpCode.CIERRE, idaYVuelta(Marco.cierre(), true).opCode());
    }

    @Test
    void elMarcoDeCierreLlevaElCodigoMilEnBigEndian() {
        byte[] datos = Marco.cierre().datos();
        assertEquals(2, datos.length);
        assertEquals(1000, ((datos[0] & 0xFF) << 8) | (datos[1] & 0xFF));
    }

    @Test
    void seDistinguenLosMarcosDeControlDeLosDeDatos() {
        assertTrue(OpCode.CIERRE.esDeControl());
        assertTrue(OpCode.PING.esDeControl());
        assertTrue(OpCode.PONG.esDeControl());
        assertFalse(OpCode.TEXTO.esDeControl());
        assertFalse(OpCode.BINARIO.esDeControl());
    }

    @Test
    void unMarcoNoFinalSeReconoceComoFragmento() throws IOException {
        Marco fragmento = new Marco(false, OpCode.TEXTO, "parte".getBytes(StandardCharsets.UTF_8));
        assertFalse(idaYVuelta(fragmento, false).fin());
    }

    @Test
    void unCodigoDeOperacionDesconocidoEsRechazado() {
        assertThrows(ProtocoloException.class, () -> OpCode.desde(0x5));
    }

    @Test
    void losBitsRsvDebenVenirEnCero() {
        // 0x40 enciende RSV1, que este protocolo no usa
        byte[] marcoInvalido = {(byte) 0xC1, 0x01, 0x41};
        assertThrows(ProtocoloException.class,
                () -> ProtocoloWebSocket.decodificar(new ByteArrayInputStream(marcoInvalido)));
    }

    @Test
    void unMarcoDeControlNoPuedeSuperarCientoVeinticincoBytes() {
        // opcode PING (0x89) con longitud declarada 126
        byte[] marcoInvalido = {(byte) 0x89, 0x7E, 0x00, (byte) 0xFF};
        assertThrows(ProtocoloException.class,
                () -> ProtocoloWebSocket.decodificar(new ByteArrayInputStream(marcoInvalido)));
    }

    @Test
    void unMarcoQueDeclaraUnTamanoEnormeEsRechazado() {
        // Sin este limite, un solo marco malicioso podria reservar gigabytes.
        byte[] marcoInvalido = {(byte) 0x81, 0x7F,
                0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertThrows(ProtocoloException.class,
                () -> ProtocoloWebSocket.decodificar(new ByteArrayInputStream(marcoInvalido)));
    }

    @Test
    void unFlujoVacioDevuelveNull() throws IOException {
        assertNull(ProtocoloWebSocket.decodificar(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void unMarcoCortadoALaMitadLanzaEof() {
        // Declara 10 bytes de datos pero solo manda 2
        byte[] incompleto = {(byte) 0x81, 0x0A, 'a', 'b'};
        assertThrows(java.io.EOFException.class,
                () -> ProtocoloWebSocket.decodificar(new ByteArrayInputStream(incompleto)));
    }

    @Test
    void unMarcoVacioEsValido() throws IOException {
        Marco vacio = new Marco(true, OpCode.TEXTO, new byte[0]);
        assertEquals("", idaYVuelta(vacio, true).comoTexto());
    }
}
