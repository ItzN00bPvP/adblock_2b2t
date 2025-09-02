package info.n00bs.adblock_2b2t.client.filter;

import info.n00bs.adblock_2b2t.client.config.FilterCategory;
import info.n00bs.adblock_2b2t.client.config.FilterConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles filtering of chat messages based on patterns.
 */
public class MessageFilter {
    private static final MessageFilter INSTANCE = new MessageFilter();

    private final List<Pattern> remotePatterns = new CopyOnWriteArrayList<>();
    private final List<Pattern> customPatterns = new CopyOnWriteArrayList<>();
    private volatile boolean isInitialized = false;

    // Scheduler for auto-refresh
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> autoRefreshTask;

    private MessageFilter() {
        // Private constructor for singleton
    }

    /**
     * Gets the singleton instance of the MessageFilter.
     * @return The MessageFilter instance
     */
    public static MessageFilter getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the filter by loading patterns from files and/or remote source.
     * Also starts the auto-refresh scheduler if enabled.
     */
    public void initialize() {
        refreshFilters();
        isInitialized = true;

        // Start auto-refresh if enabled
        startAutoRefreshIfEnabled();
    }

    /**
     * Starts the auto-refresh scheduler if enabled in the configuration.
     */
    public void startAutoRefreshIfEnabled() {
        FilterConfig config = FilterConfig.getInstance();

        // Cancel any existing task
        stopAutoRefresh();

        // Start new task if enabled
        if (config.isAutoRefreshEnabled() && config.isUseRemoteFilters()) {
            int delayMinutes = config.getAutoRefreshDelay();
            autoRefreshTask = scheduler.scheduleAtFixedRate(
                () -> {
                    System.out.println("Auto-refreshing remote filters...");
                    loadRemoteFilters().join(); // Wait for completion
                },
                delayMinutes, // Initial delay
                delayMinutes, // Periodic delay
                TimeUnit.MINUTES
            );
            System.out.println("Auto-refresh scheduled every " + delayMinutes + " minutes");
        }
    }

    /**
     * Stops the auto-refresh scheduler.
     */
    public void stopAutoRefresh() {
        if (autoRefreshTask != null) {
            autoRefreshTask.cancel(false);
            autoRefreshTask = null;
            System.out.println("Auto-refresh stopped");
        }
    }

    /**
     * Refreshes the filter patterns from local files and/or remote source.
     * @return A CompletableFuture that completes when the refresh is done
     */
    public CompletableFuture<Void> refreshFilters() {

        // Clear existing patterns
        remotePatterns.clear();
        customPatterns.clear();

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        // Load custom filters if enabled
        FilterConfig config = FilterConfig.getInstance();
        if (config.isUseCustomFilters()) {
            loadCustomFilters();
        }

        // Load remote filters if enabled
        if (config.isUseRemoteFilters()) {
            future = future.thenCompose(v -> loadRemoteFilters());
        }

        // Update auto-refresh based on current config
        future = future.thenApply(v -> {
            startAutoRefreshIfEnabled();
            return null;
        });

        return future;
    }

    /**
     * Loads custom filter patterns from local file.
     */
    private void loadCustomFilters() {
        FilterConfig config = FilterConfig.getInstance();
        String filtersDir = config.getFiltersDirectory();

        Path filePath = Paths.get(filtersDir, FilterCategory.CUSTOM_FILTERS_FILENAME);
        try {
            if (Files.exists(filePath)) {
                List<String> lines = Files.readAllLines(filePath);
                List<Pattern> patterns = compilePatterns(lines);
                customPatterns.addAll(patterns);
            }
        } catch (IOException e) {
            System.err.println("Failed to load custom filter file: " + e.getMessage());
        }
    }

    /**
     * Loads filter patterns from a remote source.
     * @return A CompletableFuture that completes when the remote filters are loaded
     */
    private CompletableFuture<Void> loadRemoteFilters() {
        return CompletableFuture.runAsync(() -> {
            FilterConfig config = FilterConfig.getInstance();
            String remoteUrl = config.getRemoteUrl();

            try {
                URL url = new URI(remoteUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        List<String> lines = new ArrayList<>();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            lines.add(line);
                        }

                        // Compile and add patterns
                        List<Pattern> patterns = compilePatterns(lines);
                        remotePatterns.addAll(patterns);

                        // Save to local file for reference
                        Path remoteFilePath = Paths.get(config.getFiltersDirectory(), FilterCategory.REMOTE_FILTERS_FILENAME);
                        Files.write(remoteFilePath, lines);
                    }
                } else {
                    System.err.println("Failed to fetch remote filters. Response code: " + responseCode);
                }
            } catch (IOException | URISyntaxException e) {
                System.err.println("Failed to load remote filters: " + e.getMessage());
            }
        });
    }

    /**
     * Compiles a list of string patterns into regex Pattern objects.
     * @param patterns The string patterns to compile
     * @return A list of compiled Pattern objects
     */
    private List<Pattern> compilePatterns(List<String> patterns) {
        return patterns.stream()
                .filter(p -> !p.isEmpty() && !p.startsWith("#")) // Skip empty lines and comments
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE)) // Compile pattern, case insensitive
                .collect(Collectors.toList());
    }

    /**
     * Checks if a message should be filtered based on the loaded patterns.
     * @param message The message to check
     * @return true if the message should be filtered, false otherwise
     */
    public boolean shouldFilterMessage(String message) {
        if (!isInitialized) {
            initialize();
        }

        FilterConfig config = FilterConfig.getInstance();
        if (!config.isEnabled()) {
            return false; // Filtering is disabled
        }

        // Check custom filters
        if (config.isUseCustomFilters()) {
            for (Pattern pattern : customPatterns) {
                if (pattern.matcher(message).find()) {
                    return true; // Message matches a custom filter pattern
                }
            }
        }

        // Check remote filters
        if (config.isUseRemoteFilters()) {
            for (Pattern pattern : remotePatterns) {
                if (pattern.matcher(message).find()) {
                    return true; // Message matches a remote filter pattern
                }
            }
        }

        return false; // No match found
    }

    /**
     * Gets the filter type that caused a message to be filtered.
     * @param message The message to check
     * @return "CUSTOM" if matched by custom filter, "REMOTE" if matched by remote filter, or null if no match
     */
    public String getMatchingFilterType(String message) {
        if (!isInitialized) {
            initialize();
        }

        FilterConfig config = FilterConfig.getInstance();
        if (!config.isEnabled()) {
            return null; // Filtering is disabled
        }

        // Check custom filters
        if (config.isUseCustomFilters()) {
            for (Pattern pattern : customPatterns) {
                if (pattern.matcher(message).find()) {
                    return "CUSTOM"; // Return custom filter type
                }
            }
        }

        // Check remote filters
        if (config.isUseRemoteFilters()) {
            for (Pattern pattern : remotePatterns) {
                if (pattern.matcher(message).find()) {
                    return "REMOTE"; // Return remote filter type
                }
            }
        }

        return null; // No match found
    }

    public Pattern getMatchingPattern(String message) {
        if (!isInitialized) {
            initialize();
        }

        FilterConfig config = FilterConfig.getInstance();
        if (!config.isEnabled()) {
            return null; // Filtering is disabled
        }

        // Check custom filters
        if (config.isUseCustomFilters()) {
            for (Pattern pattern : customPatterns) {
                if (pattern.matcher(message).find()) {
                    return pattern;
                }
            }
        }

        // Check remote filters
        if (config.isUseRemoteFilters()) {
            for (Pattern pattern : remotePatterns) {
                if (pattern.matcher(message).find()) {
                    return pattern;
                }
            }
        }

        return null; // No match found
    }
}
