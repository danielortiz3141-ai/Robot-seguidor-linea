package com.robotpid;

import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class BluetoothService {

    private final TelemetriaService telemetriaService;

    private SerialPort puerto;
    private OutputStream salida;

    private volatile boolean conectado = false;
    private volatile boolean activo = false;
    private volatile int errorPID = 0;
    private volatile int anguloRadar = 90;
    private volatile int distancia = 0;
    private volatile int estado = 0;

    // Guardar una muestra aproximadamente cada segundo.
    private static final long INTERVALO_GUARDADO_MS = 1000;

    private long ultimoGuardado = 0;

    public BluetoothService(TelemetriaService telemetriaService) {
        this.telemetriaService = telemetriaService;
    }

    @PostConstruct
    public void iniciar() {
        Thread hilo = new Thread(this::conectar, "Bluetooth-COM10");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void conectar() {

        try {
            puerto = SerialPort.getCommPort("COM10");
            puerto.setBaudRate(9600);

            puerto.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                1000,
                0
            );

            if (!puerto.openPort()) {
                System.out.println("No se pudo abrir COM10");
                return;
            }

            conectado = true;
            salida = puerto.getOutputStream();

            System.out.println("Bluetooth conectado en COM10");

            BufferedReader lector = new BufferedReader(
                new InputStreamReader(
                    puerto.getInputStream(),
                    StandardCharsets.UTF_8
                )
            );

            String linea;

            while ((linea = lector.readLine()) != null) {

                if (!linea.startsWith("$")) {
                    continue;
                }

                try {
                    String[] valores =
                        linea.substring(1).trim().split(",");

                    if (valores.length < 5) {
                        continue;
                    }

                    boolean nuevoActivo =
                        Integer.parseInt(valores[0]) == 1;

                    int nuevoError =
                        Integer.parseInt(valores[1]);

                    int nuevoAngulo =
                        Integer.parseInt(valores[2]);

                    int nuevaDistancia =
                        Integer.parseInt(valores[3]);

                    int nuevoEstado =
                        Integer.parseInt(valores[4]);

                    // Actualizar datos en tiempo real.
                    activo = nuevoActivo;
                    errorPID = nuevoError;
                    anguloRadar = nuevoAngulo;
                    distancia = nuevaDistancia;
                    estado = nuevoEstado;

                    // Guardar una lectura cada segundo.
                    long ahora = System.currentTimeMillis();

                    if (ahora - ultimoGuardado
                            >= INTERVALO_GUARDADO_MS) {

                        ultimoGuardado = ahora;

                        // Se guarda en otro hilo para que
                        // MySQL no retrase la lectura Bluetooth.
                        guardarTelemetria(
                            nuevoActivo,
                            nuevoError,
                            nuevoAngulo,
                            nuevaDistancia,
                            nuevoEstado
                        );
                    }

                } catch (NumberFormatException e) {

                    System.out.println(
                        "Lectura inválida: " + linea
                    );
                }
            }

        } catch (Exception e) {

            System.out.println(
                "Error Bluetooth: " + e.getMessage()
            );

        } finally {

            conectado = false;
            salida = null;

            if (puerto != null && puerto.isOpen()) {
                puerto.closePort();
            }

            System.out.println("Bluetooth desconectado");
        }
    }

    private void guardarTelemetria(
            boolean activo,
            int error,
            int angulo,
            int distancia,
            int estado) {

        Thread hiloGuardado = new Thread(() -> {

            telemetriaService.guardar(
                activo,
                error,
                angulo,
                distancia,
                estado
            );

        }, "Guardado-MySQL");

        hiloGuardado.setDaemon(true);
        hiloGuardado.start();
    }

    public Map<String, Object> obtenerEstado() {

        return Map.of(
            "robot", "Seguidor de linea PID",
            "conexion", "Servidor funcionando",

            "bluetooth", conectado
                ? "Conectado COM10"
                : "Desconectado",

            "activo", activo,
            "errorPID", errorPID,
            "anguloRadar", anguloRadar,
            "distancia", distancia,
            "estado", estado
        );
    }

    public synchronized boolean enviarComando(
            String comando) {

        if (!conectado || salida == null) {
            return false;
        }

        try {

            salida.write(
                (comando + "\n").getBytes(
                    StandardCharsets.UTF_8
                )
            );

            salida.flush();
            return true;

        } catch (Exception e) {

            System.out.println(
                "Error enviando comando: " + e.getMessage()
            );

            return false;
        }
    }

    @PreDestroy
    public void cerrar() {

        conectado = false;

        if (puerto != null && puerto.isOpen()) {
            puerto.closePort();
        }
    }
}