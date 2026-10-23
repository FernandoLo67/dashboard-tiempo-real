package com.fernando.dashboard;

import com.fernando.dashboard.servidor.ServidorDashboard;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        int puertoHttp = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int puertoWebSocket = args.length > 1 ? Integer.parseInt(args[1]) : 8081;

        ServidorDashboard servidor = new ServidorDashboard(puertoHttp, puertoWebSocket);
        servidor.iniciar();

        System.out.println("=== Dashboard en Tiempo Real ===");
        System.out.println("Abri http://localhost:" + servidor.getPuertoHttp() + " en el navegador");
        System.out.println("WebSocket escuchando en el puerto " + servidor.getPuertoWebSocket());
        System.out.println("Metricas en crudo: http://localhost:" + servidor.getPuertoHttp() + "/api/metricas");
        System.out.println();
        System.out.println("Ctrl+C para detener.");

        Runtime.getRuntime().addShutdownHook(new Thread(servidor::close));
    }
}
