package com.starcut.starflight_client_android;

class UnregistrationResponse implements StarFlightResponse {
    private final Result result;

    UnregistrationResponse(final Result result)
    {
        this.result = result;
    }

    enum Result
    {
        /**
         * Unregistration was successful
         */
        OK,
        /**
         * The device was not registered in the first place.
         */
        NOT_REGISTERED
    }
}
