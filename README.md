# Dashboard en Tiempo Real

Dashboard de metricas que se actualiza solo, empujado por **un servidor WebSocket escrito desde cero** siguiendo el RFC 6455. Sin Netty, sin Tomcat, sin Spring: solo `ServerSocket` y el `HttpServer` del JDK.

## Como ejecutar

```bash
mvn package
java -jar target/dashboard-tiempo-real-1.0.0.jar
```

Abrir `http://localhost:8080`. El navegador se conecta por WebSocket al 8081 y recibe metricas cada segundo. Si se corta la conexion, la pagina reintenta sola cada 2 segundos.

## El protocolo WebSocket, implementado a mano

**El apreton de manos.** El cliente manda una peticion HTTP normal con `Upgrade: websocket` y una `Sec-WebSocket-Key` aleatoria. El servidor responde `101 Switching Protocols` con:

```
Sec-WebSocket-Accept = base64( SHA-1( clave + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11" ) )
```

Ese GUID es una constante fija del estandar. No es seguridad: es una comprobacion de que el otro extremo realmente entiende WebSocket y no es un proxy despistado devolviendo cualquier cosa.

**La prueba mas util de todo el repo** es que ese calculo coincide con el vector de ejemplo del propio RFC:

```java
assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
        ProtocoloWebSocket.calcularAceptacion("dGhlIHNhbXBsZSBub25jZQ=="));
```

Si esa linea pasa, el apreton es correcto segun el estandar, no segun mi interpretacion.

**Los marcos.** Despues del apreton ya no se habla HTTP, se hablan marcos binarios:

```
byte 0:  FIN(1) RSV(3) opcode(4)
byte 1:  MASK(1) longitud(7)
  longitud == 126 -> 2 bytes mas con la longitud real
  longitud == 127 -> 8 bytes mas con la longitud real
  MASK == 1       -> 4 bytes de clave, y los datos van XOR con ella
```

Detalles que tienen prueba propia:

- **Las tres formas de codificar la longitud** y sus limites exactos: 125 usa 7 bits, 126 pasa a 16 bits, 65536 pasa a 64 bits.
- **El cliente SIEMPRE enmascara, el servidor NUNCA.** Lo exige el estandar, y hay una prueba de que los bytes enmascarados no se ven en claro en el cable.
- **Los bits RSV deben venir en cero** si no se negocio ninguna extension.
- **Un marco de control no puede pasar de 125 bytes.**
- **Limite de 10 MB por marco.** Sin el, un marco que declare 2^63 bytes haria que el servidor intentara reservar toda la memoria del mundo. Es una defensa, no un capricho.

## Las metricas

| Tipo | Que es | Ejemplo |
|---|---|---|
| Contador | Solo sube | peticiones totales, errores |
| Medidor | Sube y baja | memoria usada, clientes conectados |
| Serie | Ventana deslizante con percentiles | latencia |

Las series guardan un maximo fijo de muestras, porque un dashboard que corre dias enteros no puede acumular memoria sin limite.

**Por que percentiles y no promedio.** Hay una prueba que lo demuestra: con 95 peticiones de 20 ms y 5 de 500 ms, el promedio da 44 ms —que suena bien y no le pasa a nadie— mientras que el p99 da 500 ms, que es lo que de verdad experimenta el usuario desafortunado. Por eso el dashboard muestra p50, p95 y p99.

> Las metricas de la maquina (memoria, hilos, procesadores) son reales, salidas de la JVM. Las de trafico son simuladas, porque esto es una demo del dashboard y no de una aplicacion concreta; en el codigo estan marcadas como tales.

## Sistema de alertas

`MonitorDeAlertas` evalua reglas configurables contra el estado actual de las metricas. Cada `Alerta` define:

- **Metrica** a vigilar (medidor o contador)
- **Operador** de comparacion (`>`, `<`, `>=`, `<=`)
- **Umbral** numerico
- **Severidad** (`INFO`, `ADVERTENCIA`, `CRITICA`)

Ejemplo: alerta "memoria alta" dispara cuando `memoria_uso_porcentaje > 85` con severidad `CRITICA`. El monitor recorre todas las alertas registradas y devuelve solo las que estan disparadas en ese momento.

## Pruebas

**61 pruebas.** Ademas del vector del RFC, `ServidorDashboardTest` levanta el servidor en un puerto libre y conecta clientes WebSocket de verdad sobre loopback: verifica el apreton completo, la difusion a varios clientes, que un mensaje del cliente llegue desenmascarado, la respuesta al ping y que una desconexion limpie la lista de conectados.

```bash
mvn test
```
