package com.starcut.starflight_client_android;

public interface StarFlightCallback<T extends StarFlightResponse>
{
    /**
     * Called when the operation succeeds
     * @param result the result of the operation
     */
    void onSuccess(final T result);

    /**
     * Called when the operation fails.
     * @param message error description
     * @param throwable an exception that occurred, or null if not applicable
     */
    void onFailure(final String message, final Throwable throwable);
}
