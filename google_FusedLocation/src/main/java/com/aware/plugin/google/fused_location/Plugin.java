
package com.aware.plugin.google.fused_location;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Locations_Provider;
import com.aware.providers.Locations_Provider.Locations_Data;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Plugin;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Fused location service for Aware framework
 * Requires Google Services API available on the device.
 *
 * @author denzil
 */
public class Plugin extends Aware_Plugin implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    /**
     * Broadcasted event: new location available
     */
    public static final String ACTION_AWARE_LOCATIONS = "ACTION_AWARE_LOCATIONS";
    public static final String EXTRA_DATA = "data";

    /**
     * This plugin's package name
     */
    private final String PACKAGE_NAME = "com.aware.plugin.google.fused_location";

    private static GoogleApiClient mLocationClient;
    private final static LocationRequest mLocationRequest = new LocationRequest();
    private static PendingIntent pIntent;

    public static Location lastlocation;
    public static ContextProducer contextProducer;

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = "AWARE::Google Fused Location";

        DATABASE_TABLES = Locations_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Locations_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Locations_Data.CONTENT_URI};

        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                Intent context = new Intent(ACTION_AWARE_LOCATIONS);
                context.putExtra(Plugin.EXTRA_DATA, lastlocation);
                sendBroadcast(context);
            }
        };
        contextProducer = CONTEXT_PRODUCER;

        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);

        Intent permissions = new Intent(this, PermissionsHandler.class);
        permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
        permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(permissions);

        if (!is_google_services_available()) {
            Log.e(TAG, "Google Services fused location is not available on this device.");
            stopSelf();
        } else {
            mLocationClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApiIfAvailable(LocationServices.API)
                    .build();

            Intent locationIntent = new Intent(this, com.aware.plugin.google.fused_location.Algorithm.class);
            pIntent = PendingIntent.getService(this, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Aware.startPlugin(this, PACKAGE_NAME);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if (mLocationClient != null && !mLocationClient.isConnected()) mLocationClient.connect();
            if (mLocationClient != null && mLocationClient.isConnected()
                    && (
                    mLocationRequest.getPriority() != Integer.parseInt(Aware.getSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION))
                    || mLocationRequest.getFastestInterval() != Long.parseLong(Aware.getSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION))*1000
                    || mLocationRequest.getInterval() != Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION)) * 1000)
                    )
            {
                mLocationRequest.setPriority(Integer.parseInt(Aware.getSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION)));
                mLocationRequest.setInterval(Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION)) * 1000);
                mLocationRequest.setFastestInterval(Long.parseLong(Aware.getSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION)) * 1000);

                LocationServices.FusedLocationApi.removeLocationUpdates(mLocationClient, pIntent); //remove old
                LocationServices.FusedLocationApi.requestLocationUpdates(mLocationClient, mLocationRequest, pIntent); //add new
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Aware.setSetting(this, Settings.STATUS_GOOGLE_FUSED_LOCATION, false);

        if (mLocationClient != null && mLocationClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mLocationClient, pIntent);
            mLocationClient.disconnect();
        }
        Aware.stopPlugin(this, PACKAGE_NAME);
    }

    private boolean is_google_services_available() {
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int result = googleApi.isGooglePlayServicesAvailable(this);
        return (result == ConnectionResult.SUCCESS);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connection_result) {
        if (DEBUG)
            Log.w(TAG, "Error connecting to Google Fused Location services, will try again in 5 minutes");
    }

    @Override
    public void onConnected(Bundle arg0) {
        Log.i(TAG, "Connected to Google's Location API");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            Aware.setSetting(this, Settings.STATUS_GOOGLE_FUSED_LOCATION, true);
            if (Aware.getSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION).length() == 0) {
                Aware.setSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION, Settings.update_interval);
            } else {
                Aware.setSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION, Aware.getSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION));
            }

            if (Aware.getSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION).length() == 0) {
                Aware.setSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION, Settings.max_update_interval);
            } else {
                Aware.setSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION, Aware.getSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION));
            }

            if (Aware.getSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION).length() == 0) {
                Aware.setSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION, Settings.location_accuracy);
            } else {
                Aware.setSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION, Aware.getSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION));
            }

            mLocationRequest.setPriority(Integer.parseInt(Aware.getSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION)));
            mLocationRequest.setInterval(Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION)) * 1000);
            mLocationRequest.setFastestInterval(Long.parseLong(Aware.getSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION)) * 1000);

            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                LocationServices.FusedLocationApi.requestLocationUpdates(mLocationClient, mLocationRequest, pIntent);
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (DEBUG)
            Log.w(TAG, "Error connecting to Google Fused Location services, will try again in 5 minutes");
    }
}
