package org.example;

import org.example.resp.CentralStationConnection;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;


public final class Main {
    public static void main(String[] args) throws IOException {
        Map<String, String> values;
        Optional<String> v1;
        Optional<String> v2;
        try(CentralStationConnection connection = CentralStationConnection.connect(
                "127.0.0.1",
                6380,
                3_000,
                30_000
        )) {
            values = connection.getAll();
            v1 = connection.get("7");
            v2 = connection.get("11");
        }

        values.forEach((key, value) -> {
            System.out.println("Station_id: " + key);
            System.out.println("Value:\n" + value);
            System.out.println("**********************************************************");
        });

        if (v1.isEmpty()) {
            System.out.println("No such key with value: 7");
        } else {
            System.out.println(v1.get());
        }

        if (v2.isEmpty()) {
            System.out.println("No such key with value: 11");
        } else {
            System.out.println(v2.get());
        }

    }
}
