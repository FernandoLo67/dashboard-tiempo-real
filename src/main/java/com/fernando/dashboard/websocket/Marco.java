package com.fernando.dashboard.websocket;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Un marco (frame) de WebSocket ya interpretado. */
public final class Marco {

    private final boolean fin;
    private final OpCode opCode;
    private final byte[] datos;

    public Marco(boolean fin, OpCode opCode, byte[] datos) {
        this.fin = fin;
        this.opCode = opCode;
        this.datos = datos;
    }

    public boolean fin() {
        return fin;
    }

    public OpCode opCode() {
        return opCode;
    }

    public byte[] datos() {
        return datos;
    }

    public static Marco texto(String texto) {
        return new Marco(true, OpCode.TEXTO, texto.getBytes(StandardCharsets.UTF_8));
    }

    public static Marco ping() {
        return new Marco(true, OpCode.PING, new byte[0]);
    }

    public static Marco pong(byte[] datos) {
        return new Marco(true, OpCode.PONG, datos);
    }

    public static Marco cierre() {
        // 1000 = cierre normal, en big-endian
        return new Marco(true, OpCode.CIERRE, new byte[]{0x03, (byte) 0xE8});
    }

    public String comoTexto() {
        return new String(datos, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Marco)) {
            return false;
        }
        Marco marco = (Marco) o;
        return fin == marco.fin
                && opCode == marco.opCode
                && Objects.equals(datos, marco.datos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fin, opCode, datos);
    }

    @Override
    public String toString() {
        return opCode + "[" + datos.length + " bytes]" + (fin ? "" : " (fragmentado)");
    }
}
