package com.docklet.shell.runtime;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/** Read-only device network facts; never probes the module's relay. */
public final class NetworkWatcher {

    public interface Listener {
        void onNetworkChanged(NetworkState state);
    }

    public static final class NetworkState {
        public final boolean online;
        public final String type;
        public final boolean metered;
        public final boolean validated;
        public final long changedAt;

        NetworkState(boolean online, String type, boolean metered, boolean validated, long changedAt) {
            this.online = online;
            this.type = type;
            this.metered = metered;
            this.validated = validated;
            this.changedAt = changedAt;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("online", online);
                json.put("type", type);
                json.put("metered", metered);
                json.put("validated", validated);
                json.put("changedAt", changedAt);
            } catch (Exception ignored) {
                // Values are primitive.
            }
            return json;
        }

        boolean sameAs(NetworkState other) {
            return other != null
                    && online == other.online
                    && type.equals(other.type)
                    && metered == other.metered
                    && validated == other.validated;
        }
    }

    private final ConnectivityManager manager;
    private Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NetworkState last;
    private boolean registered;
    private boolean stopped;

    private final ConnectivityManager.NetworkCallback callback =
            new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    scheduleRefresh();
                }

                @Override
                public void onLost(Network network) {
                    scheduleRefresh();
                }

                @Override
                public void onCapabilitiesChanged(
                        Network network, NetworkCapabilities capabilities
                ) {
                    scheduleRefresh();
                }
            };

    public NetworkWatcher(Context context, Listener listener) {
        this.listener = listener;
        this.manager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE
        );
        this.last = readState();
        start();
    }

    public NetworkState state() {
        NetworkState current = readState();
        if (last == null || !current.sameAs(last)) {
            last = current;
        }
        return last;
    }

    public void stop() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        if (!registered || manager == null) return;
        try {
            manager.unregisterNetworkCallback(callback);
        } catch (Exception ignored) {
            // Already unregistered.
        }
        registered = false;
    }

    private void start() {
        if (manager == null) return;
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            manager.registerNetworkCallback(request, callback);
            registered = true;
        } catch (Exception ignored) {
            registered = false;
        }
    }

    private void scheduleRefresh() {
        if (stopped) return;
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 150L);
    }

    private final Runnable refreshRunnable = () -> {
        if (stopped) return;
        NetworkState next = readState();
        if (next.sameAs(last)) return;
        last = next;
        if (listener != null) listener.onNetworkChanged(next);
    };

    private NetworkState readState() {
        if (manager == null) {
            return new NetworkState(false, "none", false, false, 0L);
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return new NetworkState(false, "none", false, false, System.currentTimeMillis());
        }
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        if (caps == null) {
            return new NetworkState(false, "none", false, false, System.currentTimeMillis());
        }
        String type = "other";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            type = "vpn";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            type = "wifi";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            type = "cellular";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            type = "ethernet";
        }
        boolean metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        return new NetworkState(true, type, metered, validated, System.currentTimeMillis());
    }
}
