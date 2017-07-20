package com.starcut.starflight_android;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.starcut.starflight_client_android.RegistrationResponse;
import com.starcut.starflight_client_android.StarFlightCallback;
import com.starcut.starflight_client_android.StarFlightClient;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        updateStarflightCommunication();
    }

    private void updateStarflightCommunication() {
        final StarFlightClient starFlight = getStarFlightClient();

        if (starFlight.isRegistered(this)) {
            Log.d(MainActivity.class.getSimpleName(), "onCreate: It is already registered!" + starFlight.isRegistered(this));
            starFlight.refreshRegistration(this);
        }
        else {
            Log.d(MainActivity.class.getSimpleName(), "onCreate: It is not register!");
            starFlight.register(this, getStartFlightTags(), registrationCallback);
        }
    }

    /**
     * Get the client for production and for staging. Parameters can be found in Starflight page.
     * @return
     */
    private StarFlightClient getStarFlightClient() {
        if (BuildConfig.DEBUG) {
            return new StarFlightClient("00000000000", "00", "00000-0-00000-000-0000-0-000");
        } else {
            return new StarFlightClient("11111111111", "11", "1111-1-1-1111111-1-1111-1111");
        }
    }

    /**
     * Tags to subscribe to Starflight. Use to create different types of notifications.
     * @return
     */
    private List<String> getStartFlightTags() {
        List<String> tags = new ArrayList<>();
        tags.add(GcmBroadcastReceiver.TAG_NORMAL);
        tags.add(GcmBroadcastReceiver.TAG_REMIND);
        return tags;
    }

    private final StarFlightCallback<RegistrationResponse> registrationCallback = new StarFlightCallback<RegistrationResponse>()
    {
        public void onSuccess(RegistrationResponse response)
        {
            Log.d(MainActivity.class.getSimpleName(), "StarFlight Registration: Succeeded");
        }

        @Override
        public void onFailure(String message, Throwable t)
        {
            Log.d(MainActivity.class.getSimpleName(), "StarFlight Registration: Failed");
        }
    };
}
