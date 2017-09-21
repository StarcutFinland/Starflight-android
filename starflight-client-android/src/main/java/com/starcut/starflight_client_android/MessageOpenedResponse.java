package com.starcut.starflight_client_android;

class MessageOpenedResponse implements StarFlightResponse
{
	private final Result result;

	MessageOpenedResponse(final Result result)
	{
		this.result = result;
	}

	public Result getResult()
	{
		return result;
	}

	enum Result
	{
		/**
		 * The message open was recorded successfully
		 */
		OK,

		/**
		 * The opening of the message had already been recorded
		 */
		ALREADY_OPENED
	}
}
