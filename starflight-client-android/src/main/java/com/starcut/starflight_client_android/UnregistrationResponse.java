package com.starcut.starflight_client_android;

public class UnregistrationResponse implements StarFlightResponse {
    private final Result result;

    UnregistrationResponse(Result result)
    {
        this.result = result;
    }

    public enum Result
    {
        /**
         * Unregistration was successful
         */
        OK,
        /**
         * The device was not registered in the first place.
         */
        NOT_REGISTERED;
    }
}
