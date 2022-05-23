package com.bernardocmarques.smartlockclient;

import com.google.gson.JsonObject;

public class Lock {

    static String TAG = "Cycling_Fizz@Lock";

    private final String id;
    private final String mac_address;
    private final String ble_address;
    private final String name;


    private Lock(String id, String mac_address, String ble_address, String name) {
        this.id = id;
        this.mac_address = mac_address;
        this.ble_address = ble_address;
        this.name = name;
    }




    public static Lock fromJson(JsonObject json) {


        return new Lock(
                json.get("MAC").getAsString(),
                json.get("MAC").getAsString(),
                json.get("BLE").getAsString(),
                json.get("name").getAsString()
        );
    }

    public String getId() {
        return id;
    }

    public String getMacAddress() {
        return mac_address;
    }

    public String getBleAddress() {
        return ble_address;
    }

    public String getName() {
        return name;
    }
}
