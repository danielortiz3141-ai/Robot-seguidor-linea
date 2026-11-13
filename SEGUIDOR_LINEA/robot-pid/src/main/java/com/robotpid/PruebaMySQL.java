package com.robotpid;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class PruebaMySQL {

    public static void main(String[] args) {

        String url =
            "jdbc:mysql://192.168.0.10:3306/robot_pid";

        String usuario = "havir";

        String password = System.getenv("DB_PASSWORD");

        if (password == null || password.isEmpty()) {
            System.out.println(
                "ERROR: No se encontró DB_PASSWORD"
            );
            return;
        }

        try (
            Connection conexion = DriverManager.getConnection(
                url, usuario, password
            );

            Statement consulta = conexion.createStatement();

            ResultSet resultado = consulta.executeQuery(
                "SELECT COUNT(*) FROM configuracion_pid"
            )
        ) {

            System.out.println(
                "CONEXIÓN EXITOSA CON MYSQL"
            );

            if (resultado.next()) {
                System.out.println(
                    "Registros PID: " + resultado.getInt(1)
                );
            }

        } catch (Exception e) {

            System.out.println(
                "ERROR DE CONEXIÓN: " + e.getMessage()
            );

        }
    }
}