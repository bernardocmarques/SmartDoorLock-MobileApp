package com.bernardocmarques.smartlockclient;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Parcelable;

import com.google.gson.JsonObject;

import java.io.Serializable;

public class Lock implements Serializable {

    static String TAG = "Cycling_Fizz@Lock";

    private final String id;
    private final String macAddress;
    private final String bleAddress;
    private String name;
    private String iconID;
    private Bitmap icon = null;



    public Lock(String id, String macAddress, String bleAddress, String name, String iconID) {
        this.id = id;
        this.macAddress = macAddress;
        this.bleAddress = bleAddress;
        this.name = name;
        this.iconID = iconID;
        (new Utils.httpRequestImage(bitmap -> icon = bitmap)).execute(getIconURL());
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

    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", id);
        jsonObject.addProperty("MAC", macAddress);
        jsonObject.addProperty("BLE", bleAddress);
        jsonObject.addProperty("MAC", macAddress);
        jsonObject.addProperty("name", name);
        jsonObject.addProperty("icon_id", iconID);

        return jsonObject;
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

    public Bitmap getIcon() {
        if (icon == null) {
            (new Utils.httpRequestImage(bitmap -> icon = bitmap)).execute(getIconURL());
            return null;
        }
        return icon;
    }

    private String getIconURL() {
        return Utils.SERVER_URL + "/get-icon?icon_id=" + this.iconID;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void clearIcon() {
        this.icon = null;
    }

    public void setIconID(String iconID) {
        this.iconID = iconID;
        (new Utils.httpRequestImage(bitmap -> icon = bitmap)).execute(getIconURL());
    }

}
