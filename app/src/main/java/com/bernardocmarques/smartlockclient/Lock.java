package com.bernardocmarques.smartlockclient;

import android.graphics.Bitmap;
import android.location.Location;
import android.os.Handler;
import android.os.Parcelable;

import com.google.gson.JsonObject;

import java.io.Serializable;
import java.util.Objects;

public class Lock{

    static String TAG = "Cycling_Fizz@Lock";


    public enum LockState {
        LOCKED,
        UNLOCKED
    }

    private final String id;
    private final String macAddress;
    private final String bleAddress;
    private String name;
    private String iconID;
    private Bitmap icon = null;
    private Location location;

    private boolean proximityLockActive;
    private boolean proximityUnlockActive;

    private LockState lockState;

    public Lock(String id, String macAddress, String bleAddress, String name, String iconID) {
        this(id, macAddress, bleAddress, name, iconID, false, false, null);
    }

    public Lock(String id, String macAddress, String bleAddress, String name, String iconID, boolean proximityLockActive, boolean proximityUnlockActive) {
        this(id, macAddress, bleAddress, name, iconID, proximityLockActive, proximityUnlockActive, null);
    }

    public Lock(String id, String macAddress, String bleAddress, String name, String iconID, boolean proximityLockActive, boolean proximityUnlockActive, Location location) {
        this.id = id;
        this.macAddress = macAddress;
        this.bleAddress = bleAddress;
        this.name = name;
        this.iconID = iconID;
        (new Utils.httpRequestImage(bitmap -> icon = bitmap)).execute(getIconURL());
        this.location = location;
        this.proximityLockActive = proximityLockActive;
        this.proximityUnlockActive = proximityUnlockActive;
    }


    public static Lock fromJson(JsonObject json) {

        return new Lock(
                json.get("MAC").getAsString(),
                json.get("MAC").getAsString(),
                json.get("BLE").getAsString(),
                json.get("name").getAsString(),
                json.get("icon_id").getAsString(),
                json.has("proximity_lock_active") && json.get("proximity_lock_active").getAsBoolean(),
                json.has("proximity_unlock_active") && json.get("proximity_unlock_active").getAsBoolean(),
                json.has("location") ? Utils.locationFromString(json.get("location").getAsString()) : null
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
        jsonObject.addProperty("proximity_lock_active", proximityLockActive);
        jsonObject.addProperty("proximity_unlock_active", proximityUnlockActive);
        jsonObject.addProperty("location", Utils.locationToString(location));

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

    public boolean isProximityLockActive() {
        return proximityLockActive;
    }

    public boolean isProximityUnlockActive() {
        return proximityUnlockActive;
    }

    public void setProximityLockActive(boolean proximityLockActive) {
        this.proximityLockActive = proximityLockActive;
    }

    public void setProximityUnlockActive(boolean proximityUnlockActive) {
        this.proximityUnlockActive = proximityUnlockActive;
    }

    public void setName(String name) {
        this.name = name;
    }

    private void clearIcon() {
        this.icon = null;
    }

    public void setIconID(String iconID) {
        this.iconID = iconID;
        (new Utils.httpRequestImage(bitmap -> icon = bitmap)).execute(getIconURL());
    }


    private LockState getLockState() {
        return lockState;
    }

    public boolean isLocked() {
        return getLockState() == LockState.LOCKED;
    }

    public void setLockState(LockState lockState) {
        this.lockState = lockState;
    }


    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lock lock = (Lock) o;
        return Objects.equals(id, lock.id) && Objects.equals(macAddress, lock.macAddress) && Objects.equals(bleAddress, lock.bleAddress) && Objects.equals(name, lock.name) && Objects.equals(iconID, lock.iconID) && Objects.equals(icon, lock.icon) && Objects.equals(location, lock.location) && lockState == lock.lockState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, macAddress, bleAddress, name, iconID, icon, location, lockState);
    }

    public Serializable getSerializable() {
        return new LockSerializable(this.id, this.macAddress, this.bleAddress, this.name, this.iconID, this.proximityLockActive, this.proximityUnlockActive, Utils.locationToString(location));
    }

    public static Lock fromSerializable(Serializable serializable) {
        if (serializable.getClass().equals(LockSerializable.class)) {
            LockSerializable lockSerializable = (LockSerializable) serializable;
            return new Lock(
                    lockSerializable.getId(),
                    lockSerializable.getMacAddress(),
                    lockSerializable.getBleAddress(),
                    lockSerializable.getName(),
                    lockSerializable.getIconID(),
                    lockSerializable.isProximityLockActive(),
                    lockSerializable.isProximityUnlockActive(),
                    Utils.locationFromString(lockSerializable.getLocationString()));
        } else {
            return null;
        }
    }

    private static class LockSerializable implements Serializable {

        private final String id;
        private final String macAddress;
        private final String bleAddress;
        private final String name;
        private final String iconID;
        private final String locationString;
        private final boolean proximityLockActive;
        private final boolean proximityUnlockActive;

        public LockSerializable(String id, String macAddress, String bleAddress, String name, String iconID, boolean proximityLockActive, boolean proximityUnlockActive, String locationString) {
            this.id = id;
            this.macAddress = macAddress;
            this.bleAddress = bleAddress;
            this.name = name;
            this.iconID = iconID;
            this.locationString = locationString;
            this.proximityLockActive = proximityLockActive;
            this.proximityUnlockActive = proximityUnlockActive;
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

        public String getLocationString() {
            return locationString;
        }

        public boolean isProximityLockActive() {
            return proximityLockActive;
        }

        public boolean isProximityUnlockActive() {
            return proximityUnlockActive;
        }
    }
}
