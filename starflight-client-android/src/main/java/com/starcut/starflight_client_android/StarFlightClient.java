package com.starcut.starflight_client_android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class StarFlightClient
{

	private static final String TAG = "StarFlightClient";

	static final String TEXT_KEY = "text";
	static final String URL_KEY = "url";

	private static final String PUSH_SERVER_URL = "https://starflight.starcloud.us/push";

	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	private static final int RESPONSE_CODE_CREATED = 201;
	private static final int RESPONSE_CODE_OK = 200;

	/**
	 * How frequently registrations should be refreshed in milliseconds
	 */
	private static final long REGISTRATION_REFRESH_INTERVAL = 1000L * 60 * 60 * 24 * 10; // 10 days

	private static final int KEY_VERSION = 1;
	private static final String PROPERTY_REGISTRATION_ID = "registration_id_" + KEY_VERSION;
	private static final String PROPERTY_CLIENT_UUID = "client_uuid_" + KEY_VERSION;
	private static final String PROPERTY_LAST_SENT_REG_ID = "last_sent_registration_id_" + KEY_VERSION;
	private static final String PROPERTY_LAST_REGISTRATION_TIME = "last_registration_time_" + KEY_VERSION;
	private static final String PROPERTY_REGISTERED_TAGS = "registered_tags_" + KEY_VERSION;
	private static final String PROPERTY_OPENED_MESSAGES = "opened_messages_" + KEY_VERSION;

	private static final Handler CALLBACK_HANDLER = new Handler(Looper.getMainLooper());

	private final String senderId;
	private final String appId;
	private final String clientSecret;

	/**
	 * Constructs a new StarFlight Client with the supplied GCM sender id, StarFlight app id and StarFlight client secret
	 * @param senderId the GCM sender id
	 * @param appId the StarFlight app id
	 * @param clientSecret the StarFlight client secret
	 */
	public StarFlightClient(final String senderId, final String appId, final String clientSecret)
	{
		this.senderId = senderId;
		this.appId = appId;
		this.clientSecret = clientSecret;
	}

	/**
	 * Registers for push notifications
	 * @param callback callback that will be notified of success or failure
	 */
	public void register(final Activity activity,
						 final StarFlightCallback<RegistrationResponse> callback)
	{
		register(activity, null, callback);
	}

	/**
	 * Refreshes the current StarFlight registration if needed. It is advisable to call this method every time your application starts.
	 */
	public void refreshRegistration(final Activity activity)
	{
		if (!isRegistered(activity))
		{
			throw new IllegalStateException("Not registered");
		}

		final SharedPreferences preferences = getStarFlightPreferences(activity);
		final String registeredTags = preferences.getString(PROPERTY_REGISTERED_TAGS, null);
		List<String> tags = registeredTags == null ?
				Collections.<String>emptyList() :
				Arrays.asList(registeredTags.split(","));
		Log.d(TAG, "Registered flags to refresh " + tags);
		register(activity, tags, null);
	}

	/**
	 * <p>Registers for push notifications with the supplied list of tags.</p>
	 *
	 * <p>If a registration already exists, its tags will be replaced with the supplied values.</p>
	 * @param tags the tags
	 * @param callback callback that will be notified of success or failure
	 */
	public void register(final Activity activity,
						 final List<String> tags,
						 final StarFlightCallback<RegistrationResponse> callback)
	{
		if (checkPlayServices(activity))
		{
			Context context = activity.getApplicationContext();
			String registrationId = getRegistrationId(context);
			if (registrationId == null)
			{
				getRegistrationIdInBackground(context, tags, callback);
			}
			else
			{
				sendRegistrationIdIfNeeded(context, tags, callback);
			}
		}
	}

	/**
	 * Removes an existing registration
	 * @param callback callback that will be notified of success or failure
	 */
	public void unregister(final Activity activity,
						   final StarFlightCallback<UnregistrationResponse> callback)
	{
		unregister(activity, null, callback);
	}

	/**
	 * Removes the supplied list of tags from an existing registration
	 * @param callback callback that will be notified of success or failure
	 */
	public void unregister(final Activity activity,
						   final List<String> tags,
						   final StarFlightCallback<UnregistrationResponse> callback)
	{
		if (checkPlayServices(activity))
		{
			Context context = activity.getApplicationContext();
			String registrationId = getRegistrationId(context);

			if (registrationId == null)
			{
				UnregistrationResponse response = new UnregistrationResponse(UnregistrationResponse.Result.NOT_REGISTERED);
				callOnSuccess(callback, response);
				return;
			}

			sendUnregistrationInBackground(context, tags, callback);
		}
	}

	@SuppressWarnings("unchecked")
	private void sendUnregistrationInBackground(final Context context,
												final List<String> tags,
												final StarFlightCallback<UnregistrationResponse> callback)
	{
		final String registrationId = getRegistrationId(context);

		new AsyncTask<List<String>, Void, Void>()
		{
			@Override
			protected Void doInBackground(List<String> ... params)
			{
				List<String> tags = params[0];
				UnregistrationResponse response = null;

				if (tags != null && !tags.isEmpty())
				{
					// only unregister the specified tags
					// Unregister tags from server

					try
					{
						response = sendUnregistrationToBackend(registrationId, tags);
						removeTagsFromStorage(context, tags);
					}
					catch (IOException e)
					{
						callOnFailure(callback, "Unregistration failed: " + e.getMessage(), e);
					}
				}
				else
				{
					// Unregister completely
					GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);

					try
					{
						response = sendUnregistrationToBackend(registrationId, null);
						removeRegistrationFromStorage(context);
						gcm.unregister();
					}
					catch (IOException ex)
					{
						callOnFailure(callback, "Unregistration failed: " + ex.getMessage(), ex);
					}
				}

				if (response != null)
				{
					callOnSuccess(callback, response);
				}

				return null;
			}
		}.execute(tags);
	}

	@SuppressWarnings("unchecked")
	private void sendRegistrationIdIfNeeded(final Context context,
											final List<String> tags,
											final StarFlightCallback<RegistrationResponse> callback) {

		List<String> notificationTags = new ArrayList<>(tags); // ensuring that the tags variable will not be null
		Collections.sort(notificationTags);

		final SharedPreferences preferences = getStarFlightPreferences(context);
		final String lastSentId = preferences.getString(PROPERTY_LAST_SENT_REG_ID, "");
		final long lastRegistrationTime = preferences.getLong(PROPERTY_LAST_REGISTRATION_TIME, -1);
		final String registeredTags = preferences.getString(PROPERTY_REGISTERED_TAGS, "");
		final String registrationId = getRegistrationId(context);

		final boolean shouldSend =
				lastRegistrationTime == -1
				|| System.currentTimeMillis() - lastRegistrationTime > REGISTRATION_REFRESH_INTERVAL
				|| !lastSentId.equals(registrationId)
				|| !registeredTags.equals(join(notificationTags, ","));

		if (shouldSend) {
			new AsyncTask<List<String>, Void, Void>() {
				@Override
				protected Void doInBackground(List<String>... params) {
					List<String> tags = null;
					if (params.length > 0) {
						tags = params[0];
					}

					RegistrationResponse response = null;

					try {
						response = sendRegistrationIdToBackend(registrationId, tags);
						storeRegistration(context, registrationId, tags, response.getClientUuid());
					} catch (IOException e) {
						callOnFailure(callback, "Failed to send registration id to StarFlight: " + e.getMessage(), e);
					} catch (JSONException e) {
						callOnFailure(callback, "Failed to parse server response: " + e.getMessage(), e);
					}

					if (response != null) {
						callOnSuccess(callback, response);
					}

					return null;
				}
			}.execute(notificationTags, null, null);
		} else {
			RegistrationResponse response = new RegistrationResponse(getClientUuid(context), RegistrationResponse.Result.ALREADY_REGISTERED);
			callOnSuccess(callback, response);
			Log.i(TAG, "already registered and refreshing was not necessary");
		}
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If it
	 * doesn't, display a dialog that allows users to download the APK from the
	 * Google Play Store or enable it in the device's system settings.
	 */
	private static boolean checkPlayServices(final Activity activity)
	{
		int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
		if (resultCode != ConnectionResult.SUCCESS)
		{
			if (GoogleApiAvailability.getInstance().isUserResolvableError(resultCode))
			{
				GoogleApiAvailability.getInstance().getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
			}
			else
			{
				Log.e(TAG, "This device is not supported.");
			}

			return false;
		}
		return true;
	}

	/**
	 * Gets the currently active GCM registration id
	 * @return the GCM registration id, or null if none exists
	 */
	private String getRegistrationId(final Context context)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		return prefs.getString(PROPERTY_REGISTRATION_ID, null);
	}

	/**
	 * Gets the client UUID of the current registration
	 * @return the client UUID, or null if the app is not registered for notifications
	 */
	public UUID getClientUuid(final Context context)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		String uuid = prefs.getString(PROPERTY_CLIENT_UUID, null);
		return (uuid == null ? null : UUID.fromString(uuid));
	}

	private SharedPreferences getStarFlightPreferences(final Context context)
	{
		return context.getSharedPreferences(StarFlightClient.class.getSimpleName(), Context.MODE_PRIVATE);
	}

	@SuppressWarnings("unchecked")
	private void getRegistrationIdInBackground(final Context context,
											   final List<String> tags,
											   final StarFlightCallback<RegistrationResponse> callback)
	{
		new AsyncTask<List<String>, Void, Void>()
		{
			@Override
			protected Void doInBackground(List<String> ... params)
			{
				try
				{
					GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
					String registrationId = gcm.register(senderId);

					List<String> tags = null;
					if (params.length > 0)
					{
						tags = params[0];
					}

					RegistrationResponse response = sendRegistrationIdToBackend(registrationId, tags);
					storeRegistration(context, registrationId, tags, response.getClientUuid());
					callOnSuccess(callback, response);
				}
				catch (IOException ex)
				{
					callOnFailure(callback, "Registration failed: " + ex.getMessage(), ex);
				}
				catch (JSONException ex)
				{
					callOnFailure(callback, "Failed to parse registration response: " + ex.getMessage(), ex);
				}

				return null;
			}
		}.execute(tags, null, null);
	}

	private UnregistrationResponse sendUnregistrationToBackend(final String registrationId,
															   final List<String> tags) throws IOException
	{
		OkHttpClient client = new OkHttpClient();
		FormBody.Builder bodyBuilder = new FormBody.Builder();

		Map<String, String> nameValuePairs = new HashMap<>();
		nameValuePairs.put("action", "unregister");
		nameValuePairs.put("appId", appId);
		nameValuePairs.put("clientSecret", clientSecret);
		nameValuePairs.put("type", "android");
		nameValuePairs.put("token", registrationId);

		if (tags != null && !tags.isEmpty())
		{
			nameValuePairs.put("tags", join(tags, ","));
		}

		for (Map.Entry<String, String> entry : nameValuePairs.entrySet()) {
			bodyBuilder.add(entry.getKey(), entry.getValue());
		}

		Request request = new Request.Builder()
				.url(PUSH_SERVER_URL)
				.post(bodyBuilder.build())
				.build();

		Response response = client.newCall(request).execute();
		int code = response.code();

		if (code == RESPONSE_CODE_OK)
		{
			Log.i(TAG, "Unregistration successful");
		}
		else
		{
			throw new IOException("Unexpected HTTP response code: " + code);
		}

		return new UnregistrationResponse(UnregistrationResponse.Result.OK);
	}

	private RegistrationResponse sendRegistrationIdToBackend(final String registrationId,
															 final List<String> tags) throws IOException, JSONException
	{
		OkHttpClient client = new OkHttpClient();
		FormBody.Builder bodyBuilder = new FormBody.Builder();

		Map<String, String> nameValuePairs = new HashMap<>();
		nameValuePairs.put("action", "register");
		nameValuePairs.put("appId", appId);
		nameValuePairs.put("clientSecret", clientSecret);
		nameValuePairs.put("type", "android");
		nameValuePairs.put("token", registrationId);

		if (tags != null && !tags.isEmpty())
		{
			nameValuePairs.put("tags", join(tags, ","));
		}

		for (Map.Entry<String, String> entry : nameValuePairs.entrySet()) {
			bodyBuilder.add(entry.getKey(), entry.getValue());
		}

		Request request = new Request.Builder()
				.url(PUSH_SERVER_URL)
				.post(bodyBuilder.build())
				.build();

		Response response = client.newCall(request).execute();
		int code = response.code();
		ResponseBody responseBody = response.body();

		final RegistrationResponse.Result result;
		if (code == RESPONSE_CODE_CREATED) {
			result = RegistrationResponse.Result.REGISTERED;
			Log.i(TAG, "Registered push client");
		}
		else if (code == RESPONSE_CODE_OK) {
			result = RegistrationResponse.Result.REFRESHED;
			Log.i(TAG, "Push client registration refreshed");
		}
		else {
			final String responseStr = responseBody == null ? "" : responseBody.string();
			throw new IOException("Unexpected HTTP response code: " + code + ", response text: " + responseStr);
		}

		if (responseBody == null) {
			throw new IOException("Empty response body!");
		}

		JSONObject json = new JSONObject(responseBody.string());
		UUID clientUuid = UUID.fromString(json.getString("clientUuid"));

		return new RegistrationResponse(clientUuid, result);
	}

	private void storeRegistration(final Context context,
								   final String registrationId,
								   final List<String> tags,
								   final UUID clientUuid)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		Log.i(TAG, "Saving GCM registration id " + registrationId);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_REGISTRATION_ID, registrationId);
		editor.putString(PROPERTY_LAST_SENT_REG_ID, registrationId);
		editor.putLong(PROPERTY_LAST_REGISTRATION_TIME, System.currentTimeMillis());
		editor.putString(PROPERTY_REGISTERED_TAGS, join(tags, ","));
		editor.putString(PROPERTY_CLIENT_UUID, clientUuid.toString());
		editor.apply();
	}

	private void removeRegistrationFromStorage(final Context context)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		editor.clear();
		editor.apply();
	}

	private void removeTagsFromStorage(final Context context, final List<String> tags)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);

		if (tags != null && !tags.isEmpty())
		{
			List<String> previousTags = new ArrayList<>(Arrays.asList(prefs.getString(PROPERTY_REGISTERED_TAGS, "").split(",")));

			for (String tag : tags)
			{
				previousTags.remove(tag);
			}

			SharedPreferences.Editor editor = prefs.edit();
			editor.putString(PROPERTY_REGISTERED_TAGS, join(previousTags, ","));
			editor.apply();
		}
		else
		{
			// no tags specified, we remove them all
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove(PROPERTY_REGISTERED_TAGS);
			editor.apply();
		}
	}

	private static String join(final List<String> list, final String separator)
	{
		if (list != null && !list.isEmpty())
		{
			StringBuilder joined = new StringBuilder();

			for (int i = 0; i < list.size(); i++)
			{
				joined.append(list.get(i));

				if (i < list.size() - 1)
				{
					joined.append(separator);
				}
			}

			return joined.toString();
		}

		return null;
	}

	/**
	 * Tells if this app is currently registered for notifications
	 */
	public boolean isRegistered(final Context context)
	{
		return getRegistrationId(context) != null;
	}

	/**
	 * Records that the message with the supplied UUID was opened by the user
	 */
	public void messageOpened(final Context context,
							  final UUID messageUuid,
							  final StarFlightCallback<MessageOpenedResponse> callback)
	{
		if (isMessageOpened(context, messageUuid))
		{
			callOnSuccess(callback, new MessageOpenedResponse(MessageOpenedResponse.Result.ALREADY_OPENED));
			return;
		}

		final String registrationId = getRegistrationId(context);

		new AsyncTask<Void, Void, Void>()
		{
			@Override
			protected Void doInBackground(Void ... params)
			{
				try
				{
					OkHttpClient client = new OkHttpClient();
					FormBody.Builder bodyBuilder = new FormBody.Builder();

					Map<String, String> nameValuePairs = new HashMap<>();
					nameValuePairs.put("action", "message_opened");
					nameValuePairs.put("appId", appId);
					nameValuePairs.put("clientSecret", clientSecret);
					nameValuePairs.put("type", "android");
					nameValuePairs.put("token", registrationId);
					nameValuePairs.put("uuid", messageUuid.toString());

					for (Map.Entry<String, String> entry : nameValuePairs.entrySet()) {
						bodyBuilder.add(entry.getKey(), entry.getValue());
					}

					Request request = new Request.Builder()
							.url(PUSH_SERVER_URL)
							.post(bodyBuilder.build())
							.build();
					Response response = client.newCall(request).execute();
					int code = response.code();
					ResponseBody responseBody = response.body();

					if (code != RESPONSE_CODE_OK) {
						final String responseStr = responseBody == null ? "" : responseBody.string();
						throw new IOException("Unexpected HTTP response code: " + code + ", response text: " + responseStr);
					}

					storeMessageOpened(context, messageUuid);
					callOnSuccess(callback, new MessageOpenedResponse(MessageOpenedResponse.Result.OK));
				}
				catch (IOException ex)
				{
					callOnFailure(callback, "Recording message open failed: " + ex.getMessage(), ex);
				}

				return null;
			}
		}.execute(null, null, null);
	}

	/**
	 * Tells if the opening of the message with the supplied UUID has already been recorded
	 */
	private boolean isMessageOpened(final Context context, final UUID messageUuid)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		return Arrays.asList(prefs.getString(PROPERTY_OPENED_MESSAGES, "").split(",")).contains(messageUuid.toString());
	}

	/**
	 * Stores that the opening of the message with the supplied UUID has been recorded
	 */
	private void storeMessageOpened(final Context context, final UUID messageUuid)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		List<String> openedMessageUuids = Arrays.asList(prefs.getString(PROPERTY_OPENED_MESSAGES, "").split(","));

		if (!openedMessageUuids.contains(messageUuid.toString()))
		{
			String newValue = join(openedMessageUuids, ",") + (openedMessageUuids.isEmpty() ? "" : ",") + messageUuid;
			editor.putString(PROPERTY_OPENED_MESSAGES, newValue);
		}

		editor.apply();
	}

	/**
	 * Calls the onSuccess method of the supplied callback if the callback is not null
	 */
	private static <T extends StarFlightResponse> void callOnSuccess(final StarFlightCallback<T> callback,
																	 final T response)
	{
		if (callback == null)
		{
			return;
		}

		CALLBACK_HANDLER.post(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onSuccess(response);
			}
		});
	}

	/**
	 * Calls the onFailure method of the supplied callback if the callback is not null
	 */
	private static void callOnFailure(final StarFlightCallback<? extends StarFlightResponse> callback,
									  final String message,
									  final Throwable throwable)
	{
		if (callback == null)
		{
			return;
		}

		CALLBACK_HANDLER.post(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onFailure(message, throwable);
			}
		});
	}
}
