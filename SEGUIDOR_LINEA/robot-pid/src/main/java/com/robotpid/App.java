package com.robotpid;

import com.fazecast.jSerialComm.SerialPort;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class App {

    private static final String COM = "COM10";
    private static final int BAUDIOS = 9600;

    public static void main(String[] args) {

        SerialPort bluetooth = SerialPort.getCommPort(COM);

        bluetooth.setComPortParameters(
            BAUDIOS, 8,
            SerialPort.ONE_STOP_BIT,
            SerialPort.NO_PARITY
        );

        bluetooth.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
            0, 0
        );

        if (!bluetooth.openPort()) {
            System.out.println("ERROR: No se pudo abrir COM10.");
            System.out.println("Cierra PuTTY y verifica el Bluetooth.");
            return;
        }

        System.out.println("================================");
        System.out.println("   ROBOT PID - MONITOR JAVA");
        System.out.println("================================");
        System.out.println("Bluetooth conectado en COM10");
        System.out.println("Comandos: START, STOP, KP=60,");
        System.out.println("KI=0, KD=25, VEL=120, SALIR");
        System.out.println();

        // Hilo que recibe los datos del Arduino
        Thread receptor = new Thread(() -> {

            try {
                BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(
                        bluetooth.getInputStream(),
                        StandardCharsets.US_ASCII
                    )
                );

                while (!Thread.currentThread().isInterrupted()) {

                    String linea = entrada.readLine();

                    if (linea != null && !linea.isBlank()) {
                        procesarDatos(linea.trim());
                    }
                }

            } catch (Exception e) {
             System.out.println(
               "Error de recepcion: " + e.getMessage()
             );
         e.printStackTrace();
            }
        });

        receptor.setDaemon(true);
        receptor.start();

        // Enviar comandos desde el teclado
        try {
            BufferedReader teclado = new BufferedReader(
                new InputStreamReader(System.in)
            );

            OutputStream salida = bluetooth.getOutputStream();

            while (true) {

                String comando = teclado.readLine();

                if (comando == null) break;

                comando = comando.trim().toUpperCase();

                if (comando.equals("SALIR")) break;
                if (comando.isEmpty()) continue;

                salida.write(
                    (comando + "\n").getBytes(
                        StandardCharsets.US_ASCII
                    )
                );

                salida.flush();

                System.out.println(
                    "Comando enviado: " + comando
                );
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            receptor.interrupt();
            bluetooth.closePort();
            System.out.println("Bluetooth desconectado.");
        }
    }

    private static void procesarDatos(String mensaje) {

        if (!mensaje.startsWith("$")) {
            System.out.println("Arduino: " + mensaje);
            return;
        }

        try {
            String[] datos = mensaje.substring(1).split(",");

            if (datos.length != 5) return;

            int id = Integer.parseInt(datos[0]);
            int error = Integer.parseInt(datos[1]);
            int angulo = Integer.parseInt(datos[2]);
            int distancia = Integer.parseInt(datos[3]);
            int estado = Integer.parseInt(datos[4]);

            String distanciaTexto = distancia == 999
                ? "Sin eco"
                : distancia + " cm";

            System.out.printf(
                "Robot %d | Error: %+d | Radar: %d° | " +
                "Distancia: %s | Estado: %d%n",
                id, error, angulo, distanciaTexto, estado
            );

        } catch (NumberFormatException e) {
            System.out.println("Dato invalido: " + mensaje);
        }
    }
}