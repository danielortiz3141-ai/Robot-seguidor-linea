package com.robotpid;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelemetriaService {

    private final String url;
    private final String usuario;
    private final String password;

    public TelemetriaService(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String usuario,
            @Value("${spring.datasource.password}") String password) {

        this.url = url;
        this.usuario = usuario;
        this.password = password;
    }

    public void guardar(
            boolean activo,
            int errorPid,
            int anguloRadar,
            int distanciaCm,
            int estadoRobot) {

        String sql = """
            INSERT INTO telemetria
            (activo, error_pid, angulo_radar,
             distancia_cm, estado_robot)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conexion = DriverManager.getConnection(
                url, usuario, password);
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setBoolean(1, activo);
            ps.setInt(2, errorPid);
            ps.setInt(3, anguloRadar);
            ps.setInt(4, distanciaCm);
            ps.setInt(5, estadoRobot);

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println(
                "Error guardando telemetría: " + e.getMessage()
            );
        }
    }

    public List<Map<String, Object>> obtenerHistorial(int limite) {

        int cantidad = Math.max(1, Math.min(limite, 500));

        String sql = """
            SELECT id, fecha_hora, activo, error_pid,
                   angulo_radar, distancia_cm, estado_robot
            FROM telemetria
            ORDER BY id DESC
            LIMIT ?
            """;

        List<Map<String, Object>> historial = new ArrayList<>();

        try (Connection conexion = DriverManager.getConnection(
                url, usuario, password);
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setInt(1, cantidad);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    historial.add(Map.of(
                        "id", rs.getLong("id"),
                        "fechaHora",
                            rs.getTimestamp("fecha_hora").toString(),
                        "activo", rs.getBoolean("activo"),
                        "errorPid", rs.getInt("error_pid"),
                        "anguloRadar", rs.getInt("angulo_radar"),
                        "distanciaCm", rs.getInt("distancia_cm"),
                        "estadoRobot", rs.getInt("estado_robot")
                    ));
                }
            }

        } catch (SQLException e) {
            System.err.println(
                "Error consultando historial: " + e.getMessage()
            );
        }

        return historial;
    }
}