package com.bernardocmarques.smartlockclient;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.Map;

public class Cache implements Serializable {
    private final static String TAG = "Cycling_Fizz@Cache";

    @SuppressLint("StaticFieldLeak")
    private static Cache INSTANCE_BIG = null;
    @SuppressLint("StaticFieldLeak")
    private static Cache INSTANCE_SMALL = null;

    private final static int ONE_DAY = 24;

    private String cacheFileName = "BigCache";

    private transient Context context;


    private transient LruCache<String, CacheEntry> lruCache;

    private Cache() {
        int cacheSize = 20 * 1024 * 1024; // 10MiB

        lruCache = new LruCache<>(cacheSize);
    };

    public static Cache getInstanceBigFiles() {
        if (INSTANCE_BIG == null) {
            INSTANCE_BIG = new Cache();
            INSTANCE_BIG.cacheFileName = "BigCache";
        }
        return(INSTANCE_BIG);
    }

    public static Cache getInstanceSmallFiles() {
        if (INSTANCE_SMALL == null) {
            INSTANCE_SMALL = new Cache();
            INSTANCE_SMALL.cacheFileName = "SmallCache";
        }
        return(INSTANCE_SMALL);
    }

    public static Cache getInstanceBigFiles(Context context) {
        if (INSTANCE_BIG == null) {
            INSTANCE_BIG = new Cache();

            INSTANCE_BIG.cacheFileName = "BigCache";
            INSTANCE_BIG.context = context;
            INSTANCE_BIG.getFromFile();
        }
        return(INSTANCE_BIG);
    }

    public static Cache getInstanceSmallFiles(Context context) {
        if (INSTANCE_SMALL == null) {
            INSTANCE_SMALL = new Cache();

            INSTANCE_SMALL.cacheFileName = "SmallCache";
            INSTANCE_SMALL.context = context;
            INSTANCE_SMALL.getFromFile();
        }
        return(INSTANCE_SMALL);
    }


    public void save(String key, byte[] bytes) {
        lruCache.put(key, new CacheEntry(bytes));
        saveToFile();
    }

    public byte[] get(String key) {
        CacheEntry cacheEntry = lruCache.get(key);
        if (cacheEntry != null) {
            checkAndDeleteEntry(key);
            return lruCache.get(key).getBytes();
        } else {
            return null;
        }
    }

    private void checkAndDeleteEntry(String key) {
        if (lruCache.get(key) != null && !lruCache.get(key).isValid(ONE_DAY)) {
            lruCache.remove(key);
        }
    }

    private void saveToFile() {
        if (context == null) {
            Log.w(TAG, "Couldn't save to file, context is still null");
            return;
        }
        File file = new File(context.getCacheDir(), cacheFileName);

        try {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeInt(lruCache.maxSize());
            os.writeObject(lruCache.snapshot());
            os.close();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void getFromFile() {
        if (context == null) {
            Log.e(TAG, "Couldn't get from file, context is still null");
            return;
        }
        File file = new File(context.getCacheDir(), cacheFileName);

        try {
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            lruCache = new LruCache<>(ois.readInt());
            Map<String, CacheEntry> snapshot = (Map<String, CacheEntry>) ois.readObject();

            for (String key : snapshot.keySet()) {
                lruCache.put(key, snapshot.get(key));
            }
        } catch (IOException | ClassNotFoundException ignored) {
        }
    }

    private static class CacheEntry {

        private final byte[] bytes;
        private final LocalDate storedDate;

        public CacheEntry(byte[] bytes) {
            this.bytes = bytes;
            this.storedDate = LocalDate.now();
        }


        public byte[] getBytes() {
            return bytes;
        }

        public boolean isValid(int validityHours) {
            Period periodAfterCreation = this.storedDate.until(LocalDate.now());
            return periodAfterCreation.getDays() <= validityHours;
        }
    }
}
