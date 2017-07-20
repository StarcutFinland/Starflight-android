# Starflight-android

StarFlight PushNotification Android API https://starflight.starcloud.us/docs/

## Example

The example project is only meant to show how to use the API. 
Make sure to set the real Sender ID, App Id and Client Secret for StarFlightClient.

## Requirements

- Account must be active in Starflight
- Project must have the google-services.json from Firebase. 

Remember that Starflight Client uses GCM.

## Installation

StarFlight is available through JCetner. To install
it, simply add the following line to your build.gradle:

```Dsl
 compile 'com.starcut.starflight:starflight-client-android:1.0'
```

## Author

Starcut Developers, starcutdev@gmail.com

## License

StarFlight is available under the MIT license. See the LICENSE file for more info. 

## Usage

Create a BroadcastReceiver extending StarFlightBroadcastReceiver and register it in AndroidManifest.xml

Override method onReceive to process your nofitications with different tags.

In your Activity, in onCreate add the method:

```Java
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
```
Set your Starflight client:

```Java
private StarFlightClient getStarFlightClient() {
        if (BuildConfig.DEBUG) {
            return new StarFlightClient("00000000000", "00", "00000-0-00000-000-0000-0-000");
        } else {
            return new StarFlightClient("11111111111", "11", "1111-1-1-1111111-1-1111-1111");
        }
    }
```

Declare tags to register
```Java
private List<String> getStartFlightTags() {
        List<String> tags = new ArrayList<>();
        tags.add(GcmBroadcastReceiver.TAG_NORMAL);
        tags.add(GcmBroadcastReceiver.TAG_REMIND);
        return tags;
    }
```

Implement StarFlightCallback to know when registration has succeeded
```Java
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
```
