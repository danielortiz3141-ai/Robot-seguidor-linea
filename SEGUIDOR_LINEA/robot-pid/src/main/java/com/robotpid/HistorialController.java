package com.robotpid;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HistorialController {

    private final TelemetriaService telemetriaService;

    public HistorialController(TelemetriaService telemetriaService) {
        this.telemetriaService = telemetriaService;
    }

    @GetMapping("/historial")
    public List<Map<String, Object>> historial(
            @RequestParam(defaultValue = "50") int limite) {

        return telemetriaService.obtenerHistorial(limite);
    }
}