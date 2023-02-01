package spotify.bot.service.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Service;

@Service
public class SpotifyOptimizedExecutorService {

  /**
   * The amount of threads used to fetch multiple entities from Spotify at once.
   * The specific amount of threads was decided by trial-and-error,
   * as anything greater than 5 concurrent threads will give diminishing returns.
   * <pre>Test results (with roughly 300 followed artists):
   *  1: 38075ms
   *  2: 12950ms
   *  3:  8153ms
   *  4:  5897ms
   *  5:  5276ms
   *  6:  5858ms
   * 10:  5029ms
   * 20:  5707ms
   * </pre>
   */
  private static final int ALBUM_FETCH_THREAD_COUNT = 5;

  private final ExecutorService executorService;

  SpotifyOptimizedExecutorService() {
    this.executorService = Executors.newFixedThreadPool(ALBUM_FETCH_THREAD_COUNT);
  }

  /**
   * Accepts a list of callables producing, all producing items for the same listed result,
   * in a multi-threaded manner optimized for Spotify's API (more specifically: the maximum
   * throughput with the minimal amount of 429 errors).
   *
   * This method blocks until all results have been acquired.
   *
   * @param callables the list of callables
   * @param <T> the resulting type
   * @return the full list of results
   */
  public <T> List<T> executeAndWait(List<Callable<List<T>>> callables) {
    List<Future<List<T>>> futures = new ArrayList<>();
    for (Callable<List<T>> callable : callables) {
      futures.add(executorService.submit(callable));
    }
    List<T> allResults = new ArrayList<>();
    for (Future<List<T>> future : futures) {
      try {
        List<T> result = future.get();
        allResults.addAll(result);
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    }
    return allResults;
  }

  /**
   * Same as executeAndWait, but ignoring any results.
   * @param callables the list of callables
   */
  public void executeAndWaitVoid(List<Callable<Void>> callables) {
    List<Future<Void>> futures = new ArrayList<>();
    for (Callable<Void> callable : callables) {
      futures.add(executorService.submit(callable));
    }
    for (Future<Void> future : futures) {
      try {
        future.get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    }
  }

}
