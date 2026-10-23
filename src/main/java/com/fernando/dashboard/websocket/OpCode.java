package com.fernando.dashboard.websocket;

/** Codigos de operacion del RFC 6455, seccion 5.2. */
public enum OpCode {

    CONTINUACION(0x0),
    TEXTO(0x1),
    BINARIO(0x2),
    CIERRE(0x8),
    PING(0x9),
    PONG(0xA);

    private final int codigo;

    OpCode(int codigo) {
        this.codigo = codigo;
    }

    public int getCodigo() {
        return codigo;
    }

    public boolean esDeControl() {
        return codigo >= 0x8;
    }

    public static OpCode desde(int codigo) {
        for (OpCode opCode : values()) {
            if (opCode.codigo == codigo) {
                return opCode;
            }
        }
        throw new ProtocoloException("Codigo de operacion desconocido: 0x" + Integer.toHexString(codigo));
    }
}
