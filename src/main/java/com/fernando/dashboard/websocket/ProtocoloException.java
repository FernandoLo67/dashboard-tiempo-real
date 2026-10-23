package com.fernando.dashboard.websocket;

public class ProtocoloException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtocoloException(String mensaje) {
        super(mensaje);
    }
}
