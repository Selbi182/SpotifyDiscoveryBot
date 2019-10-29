package spotify.bot.api;

import java.io.IOException;
import java.util.concurrent.Callable;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.BadGatewayException;
import com.wrapper.spotify.exceptions.detailed.InternalServerErrorException;
import com.wrapper.spotify.exceptions.detailed.NotFoundException;
import com.wrapper.spotify.exceptions.detailed.TooManyRequestsException;
import com.wrapper.spotify.requests.IRequest;

import spotify.bot.Config;
import spotify.bot.util.Constants;

public class SpotifyApiRequest {

	private SpotifyApiRequest() {
	}

	/**
	 * Executes a greedy API request, meaning that on potential <i>429 Too many
	 * requests</i> errors the request will be retried until it succeeds. Any
	 * attempts will be delayed by the response body's given <code>retryAfter</code>
	 * parameter, in seconds.
	 * 
	 * @param request
	 * @return
	 * @throws Exception 
	 */
	public static <T> T execute(IRequest<T> request) {
		try {
			return execute(new Callable<T>() {
				@Override
				public T call() throws Exception {
					return request.execute();
				}
			});			
		} catch (Exception e) {
			Config.logStackTrace(e);
			return null;
		}
	}

	/**
	 * Executes a greedy API request, wrapped in a <code>Callable</code>, meaning
	 * that on potential <i>429 Too many requests</i> errors the request will be
	 * retried until it succeeds. Any attempts will be delayed by the response
	 * body's given <code>retryAfter</code> parameter, in seconds. 5xx errors will be
	 * given a fixed one-minute retry timeout.
	 * 
	 * @param request
	 * @return
	 * @throws Exception 
	 * @throws SpotifyWebApiException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static <T> T execute(Callable<T> callable) {
		try {
			while (true) {
				try {
					T t = callable.call();
					return t;
				} catch (TooManyRequestsException e) {
					int timeout = e.getRetryAfter() + 1;
					Thread.sleep(timeout * Constants.RETRY_TIMEOUT_4XX);
				} catch (InternalServerErrorException | BadGatewayException | NotFoundException e) {
					Thread.sleep(Constants.RETRY_TIMEOUT_5XX);
				}
			}			
		} catch (Exception e) {
			Config.logStackTrace(e);
			return null;
		}
	}
}
