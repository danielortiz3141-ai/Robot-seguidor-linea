package com.robotpid;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class RobotController {

    private final BluetoothService bluetooth;

    public RobotController(BluetoothService bluetooth) {
        this.bluetooth = bluetooth;
    }

    @GetMapping("/estado")
    public Map<String, Object> obtenerEstado() {
        return bluetooth.obtenerEstado();
    }

    @PostMapping("/comando")
    public ResponseEntity<Map<String, Object>> comando(
            @RequestBody Map<String, String> datos) {

        String comando = datos.getOrDefault(
            "comando", ""
        ).trim().toUpperCase();

        Set<String> permitidos = Set.of(
            "START", "STOP", "STATUS", "DIST",
            "RADAR ON", "RADAR OFF",
            "EVASION ON", "EVASION OFF"
        );

        if (!permitidos.contains(comando) &&
            !comando.matches(
                "(KP|KI|KD|VEL)=-?\\d+(\\.\\d+)?"
            )) {
            return ResponseEntity.badRequest().body(
                Map.of("mensaje", "Comando no permitido")
            );
        }

        boolean enviado =
            bluetooth.enviarComando(comando);

        if (!enviado) {
            return ResponseEntity.status(503).body(
                Map.of(
                    "mensaje",
                    "Bluetooth no disponible"
                )
            );
        }

        return ResponseEntity.ok(
            Map.of("mensaje", "Comando enviado: " + comando)
        );
    }
}