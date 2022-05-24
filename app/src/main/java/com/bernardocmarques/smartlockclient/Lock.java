package com.bernardocmarques.smartlockclient;

import com.google.gson.JsonObject;

public class Lock {

    static String TAG = "Cycling_Fizz@Lock";

    private final String id;
    private final String macAddress;
    private final String bleAddress;
    private final String name;
    private final String iconID;


    private Lock(String id, String macAddress, String bleAddress, String name, String iconID) {
        this.id = id;
        this.macAddress = macAddress;
        this.bleAddress = bleAddress;
        this.name = name;
        this.iconID = iconID;
    }




    public static Lock fromJson(JsonObject json) {


        return new Lock(
                json.get("MAC").getAsString(),
                json.get("MAC").getAsString(),
                json.get("BLE").getAsString(),
                json.get("name").getAsString(),
                json.get("icon_id").getAsString()
        );
    }

    public String getId() {
        return id;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getBleAddress() {
        return bleAddress;
    }

    public String getName() {
        return name;
    }

    public String getIconID() {
        return iconID;
    }

    public String getIconURL() {
        return Utils.SERVER_URL + "/get-icon?icon_id=" + this.iconID;
    }
}
